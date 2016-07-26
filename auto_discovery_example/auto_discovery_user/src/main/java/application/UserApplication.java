package application;

import com.hazelcast.core.*;
import discovery.InstanceDetails;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
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

    public static final String APPLICATION_PATH = "api";
    public static final String CONTEXT_PATH = "services";
    public static final String USER_SERVICE_PATH = "/user";
    private static final String ZK_HOST = "localhost:2181";

    private CuratorFramework curator;
    private ServiceDiscovery<InstanceDetails> discovery;
	
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

        //initZKDiscovery();
        initHazelcastDiscovery();

        startHttpServer();

		startFuture.complete();
	}

    private void initZKDiscovery() throws Exception {
        createCurator();
        this.curator.start();

        createServiceDiscovery();
        this.discovery.start();

        registerService();
    }

    public void createCurator() {
        curator = CuratorFrameworkFactory.newClient(ZK_HOST, new ExponentialBackoffRetry(1000, 3));
    }

    private void createServiceDiscovery() {
        JsonInstanceSerializer<InstanceDetails> serializer =
                new JsonInstanceSerializer<InstanceDetails>(InstanceDetails.class);

        this.discovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class)
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

        discovery.registerService(instance);
    }

    private UriSpec getUriSpec(final String servicePath) {
        return new UriSpec(String.format("{scheme}://%s:{port}/%s/%s%s", "127.0.0.1",
                CONTEXT_PATH, APPLICATION_PATH, servicePath));
    }

    private void initHazelcastDiscovery() {
        // either have hazelcast.xml in classpath or pass config file using -Dhazelcast.config
        HazelcastInstance instance = Hazelcast.newHazelcastInstance();
        com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap("user");
        multiMap.put("service", "http://127.0.0.1:" + port + "/services/user");
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

        this.discovery.close();
        this.curator.close();

        //CloseableUtils.closeQuietly(this.discovery);
        //CloseableUtils.closeQuietly(this.curator);

		stopFuture.complete();
	}

	public static void main(String[] args) {
		port = Integer.parseInt(args[0]);
		
		VertxOptions options = new VertxOptions().setWorkerPoolSize(10);		
		Vertx vertx = Vertx.vertx(options);

        UserApplication userverticle = new UserApplication();

		vertx.deployVerticle(userverticle, new Handler<AsyncResult<String>>() {
			@Override
			public void handle(AsyncResult<String> result) {
				System.out.println("UserApplication");
			}
		});
	}
}
