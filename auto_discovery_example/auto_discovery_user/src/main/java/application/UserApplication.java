package application;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import discovery.DiscoveryTypeEnum;
import discovery.InstanceDetails;
import discovery.Util;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.UriSpec;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

public class UserApplication extends AbstractVerticle {
	
	private static int port = 0;
    private static String discoveryTypeStr;

    private static DiscoveryTypeEnum discoveryType = DiscoveryTypeEnum.ZOOKEEPER;

    public static final String APPLICATION_PATH = "api";
    public static final String CONTEXT_PATH = "services";
    public static final String USER_SERVICE_PATH = "/user";
    private static final String ZK_HOST = "localhost:2181";

    private CuratorFramework curator;
    private ServiceDiscovery<InstanceDetails> zkDiscovery;
    private io.vertx.servicediscovery.ServiceDiscovery vertxServiceDiscovery;
    private Record record;
	
	@Override
	public void start(Future<Void> startFuture) throws Exception {
		System.out.println("Start UserApplication");

		if (port == 0) {
			String httpPort = System.getProperty("http.port");
			if (httpPort != null) {
				port = Integer.parseInt(httpPort);
			}
		}

        if (port == 0) {
            port = config().getInteger("http.port");
        }

        if (discoveryTypeStr == null) {
            discoveryTypeStr = System.getProperty("discoveryType");
        }

        if (discoveryTypeStr == null) {
            discoveryTypeStr = config().getString("discoveryType");
        }

        discoveryType = Util.getDiscoveryEnum(discoveryTypeStr);

        if (discoveryType == DiscoveryTypeEnum.ZOOKEEPER)
            initZKDiscovery();
        else if (discoveryType == DiscoveryTypeEnum.HAZELCAST)
            initHazelcastDiscovery();
        else if (discoveryType == DiscoveryTypeEnum.VERTX)
            initVertxServiceDiscovery();

        startHttpServer();

		startFuture.complete();
	}

    private void initZKDiscovery() throws Exception {
        createCurator();
        this.curator.start();

        createServiceDiscovery();
        this.zkDiscovery.start();

        registerService();
    }

    private void createCurator() {
        curator = CuratorFrameworkFactory.newClient(ZK_HOST, new ExponentialBackoffRetry(1000, 3));
    }

    private void createServiceDiscovery() {
        JsonInstanceSerializer<InstanceDetails> serializer =
                new JsonInstanceSerializer<InstanceDetails>(InstanceDetails.class);

        this.zkDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class)
                .client(this.curator)
                .basePath("services")
                .serializer(serializer)
                .build();
    }

    private void registerService() throws Exception {
        final ServiceInstance<InstanceDetails> instance =
                ServiceInstance.<InstanceDetails>builder()
                        .name("user")
                        .payload(new InstanceDetails("1.0"))
                        .port(port)
                        .uriSpec(getUriSpec(USER_SERVICE_PATH))
                        .build();

        zkDiscovery.registerService(instance);
    }

    private UriSpec getUriSpec(final String servicePath) {
        return new UriSpec(String.format("{scheme}://%s:{port}/%s/%s%s", "127.0.0.1",
                CONTEXT_PATH, APPLICATION_PATH, servicePath));
    }

    private void initHazelcastDiscovery() {
        // either have hazelcast.xml in classpath or pass config file using -Dhazelcast.config
        Config config = new Config();
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap("user");
        multiMap.put("service", "http://127.0.0.1:" + port + "/services/user");
    }

    private void initVertxServiceDiscovery() {
        System.out.println("initVertxServiceDiscovery");

        //vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx);
        vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx,
                new ServiceDiscoveryOptions().setAnnounceAddress("user-service-announce")
                        .setName("user-name"));

        // EventBusService, HttpEndpoint, MessageSource, JDBCDataSource

        record = new Record()
                .setType("eventbus-service-proxy")
                .setLocation(new JsonObject().put("endpoint", "http://127.0.0.1:" + port + "/services/user"))
                .setName("user-service")
                .setMetadata(new JsonObject().put("some-label", "some-value"));

        vertxServiceDiscovery.publish(record, ar -> {
            if (ar.succeeded()) {
                Record publishedRecord = ar.result();
                System.out.println("publishedRecord:" + publishedRecord);
            } else {
                System.out.println("publication failed");
            }
        });


        // Record creation from HttpEndpoint
        /*
        // http
        record = HttpEndpoint.createRecord("user-service", "127.0.0.1", port, "/services/user");
        // https:
        // record = HttpEndpoint.createRecord("user-service", true, "127.0.0.1", port, "/services/user", new JsonObject().put("some-metadata", "some value"));

        System.out.println("initVertxServiceDiscovery record:" + record);

        vertxServiceDiscovery.publish(record, ar -> {
            if (ar.succeeded()) {
                // publication succeeded
                Record publishedRecord = ar.result();
                System.out.println("publishedRecord:" + publishedRecord);
            } else {
                System.out.println("publication failed");
            }
        });
        */

        /*
        // Record creation from EventBusService
        record = EventBusService.createRecord(
                "user-service", // The service name
                "address", // the service address,
                "examples.MyService", // the service interface as string
                new JsonObject().put("some-metadata", "some value")
        );

        vertxServiceDiscovery.publish(record, ar -> {
            // ...
        });*/

    }

    private void withdrawVertxServiceDiscovery(Record record) {
        vertxServiceDiscovery.unpublish(record.getRegistration(), ar -> {
            if (ar.succeeded()) {
                System.out.println("Service Withdrawn failed");
            } else {
                // cannot un-publish the service, may have already been removed, or the record is not published
            }
        });
    }

	private void startHttpServer() {
		HttpServer server = vertx.createHttpServer();
		Router router = Router.router(vertx);

		initializeRESTServices(router);

		server.requestHandler(router::accept).listen(port);
	}

	private void initializeRESTServices(Router router) {
		router.route().handler(BodyHandler.create());

        router.get("/services/users/:id").handler(routingContext -> {
            String id = routingContext.request().getParam("id");
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/html");
            response.end("Hello " + id + ". I am running user service running on " + port);
        });

        router.get("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/html");
            response.end("Hello. I am running user service running on " + port);
        });
	}

	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		System.out.println("Stop");

        if (this.vertxServiceDiscovery != null) {
            withdrawVertxServiceDiscovery(this.record);
            this.vertxServiceDiscovery.close();
        }

        if (this.zkDiscovery != null)
            this.zkDiscovery.close();

        if (this.curator != null)
            this.curator.close();

        //CloseableUtils.closeQuietly(this.zkDiscovery);
        //CloseableUtils.closeQuietly(this.curator);

		stopFuture.complete();
	}

	public static void main(String[] args) {
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        } else if (args.length == 2) {
            port = Integer.parseInt(args[0]);
            discoveryTypeStr =  args[1];
        }
		
		VertxOptions options = new VertxOptions().setWorkerPoolSize(10);		
		Vertx vertx = Vertx.vertx(options);

        UserApplication userverticle = new UserApplication();

		vertx.deployVerticle(userverticle, new Handler<AsyncResult<String>>() {
			@Override
			public void handle(AsyncResult<String> result) {
				System.out.println("User Application deployed");
			}
		});
	}
}
