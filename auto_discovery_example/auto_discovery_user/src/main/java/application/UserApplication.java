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
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.UriSpec;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

/**
 * abhsinh2
 */
public class UserApplication extends AbstractVerticle {

    private static DiscoveryTypeEnum discoveryType = DiscoveryTypeEnum.ZOOKEEPER;

    public static final String APPLICATION_PATH = "api";
    public static final String CONTEXT_PATH = "services";
    public static final String USER_SERVICE_PATH = "/user";
    private static String host = "127.0.0.1";

    private UriSpec uriSpec;

    // Zookeeper variables and constants
    private static final String ZK_HOST = "localhost:2181";
    private CuratorFramework zkCurator;
    private ServiceDiscovery<InstanceDetails> zkDiscovery;
    private ServiceInstance<InstanceDetails> zkServiceInstance;

    // Hazelcast variables and constants
    private HazelcastInstance instance;

    // Vertx variables and constants
    private io.vertx.servicediscovery.ServiceDiscovery vertxServiceDiscovery;
    private Record record;

    private int port = 0;
    private String discoveryTypeStr;

    public UserApplication(int port, String discoveryTypeStr) {
        this.port = port;
        this.discoveryTypeStr = discoveryTypeStr;
    }
	
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
        uriSpec = this.getUriSpec(USER_SERVICE_PATH);

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
        this.zkCurator.start();

        createServiceDiscovery();
        this.zkDiscovery.start();

        registerService();
    }

    private void createCurator() {
        zkCurator = CuratorFrameworkFactory.newClient(ZK_HOST, new ExponentialBackoffRetry(1000, 3));
    }

    private void createServiceDiscovery() {
        JsonInstanceSerializer<InstanceDetails> serializer =
                new JsonInstanceSerializer<InstanceDetails>(InstanceDetails.class);

        this.zkDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class)
                .client(this.zkCurator)
                .basePath(Util.zk_basePath)
                .serializer(serializer)
                .build();
    }

    private void registerService() throws Exception {
        zkServiceInstance = ServiceInstance.<InstanceDetails>builder()
                        .name(Util.zk_usernode)
                        .payload(new InstanceDetails("1.0"))
                        .port(port)
                        .uriSpec(uriSpec)
                        .build();

        zkDiscovery.registerService(zkServiceInstance);
    }

    private UriSpec getUriSpec(final String servicePath) {
        return new UriSpec(String.format("{scheme}://%s:{port}/%s/%s%s", host,
                CONTEXT_PATH, APPLICATION_PATH, servicePath));
    }

    private void initHazelcastDiscovery() {
        // either have hazelcast.xml in classpath or pass config file using -Dhazelcast.config
        Config config = new Config();
        instance = Hazelcast.newHazelcastInstance(config);
        com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap(Util.hz_userMapName);
        multiMap.put(Util.hz_userServiceKey, uriSpec.build());
    }

    private void initVertxServiceDiscovery() {
        System.out.println("initVertxServiceDiscovery");

        //vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx);
        vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx,
                new ServiceDiscoveryOptions().setAnnounceAddress(Util.vertx_discoveryAnnounceAddress)
                        .setName(Util.vertx_discoveryName));

        // EventBusService, HttpEndpoint, MessageSource, JDBCDataSource

        record = new Record()
                .setType(EventBusService.TYPE)
                .setLocation(new JsonObject().put(Util.vertx_discoveryServiceKey, uriSpec.build()))
                .setName(Util.vertx_discoveryServiceName)
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
        record = HttpEndpoint.createRecord("user-service", host, port, "/services/user");
        // https:
        // record = HttpEndpoint.createRecord("user-service", true, host, port, "/services/user", new JsonObject().put("some-metadata", "some value"));

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
                System.out.println("Service Withdrawn Success");
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
		System.out.println("Stoping User Verticle");

        if (this.vertxServiceDiscovery != null) {
            withdrawVertxServiceDiscovery(this.record);
            this.vertxServiceDiscovery.close();
        }

        if (this.zkDiscovery != null) {
            zkDiscovery.unregisterService(zkServiceInstance);
            this.zkDiscovery.close();
        }

        if (this.zkCurator != null)
            this.zkCurator.close();

        if (instance != null) {
            com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap(Util.hz_userMapName);
            if (multiMap != null) {
                multiMap.remove(Util.hz_userServiceKey, uriSpec.build());
            }
        }

        //CloseableUtils.closeQuietly(this.zkDiscovery);
        //CloseableUtils.closeQuietly(this.zkCurator);

		stopFuture.complete();
	}
}
