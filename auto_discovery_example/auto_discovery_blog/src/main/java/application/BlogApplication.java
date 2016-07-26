package application;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
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
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.apache.log4j.net.SyslogAppender;

import java.util.Collection;

public class BlogApplication extends AbstractVerticle {

    private static int port = 0;

    private static final String ZK_HOST = "localhost:2181";

    private CuratorFramework curator;
    private ServiceDiscovery<InstanceDetails> discovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        System.out.println("Start BlogApplication");

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

        retrieveUserServices();
    }

    public void createCurator() {
        this.curator = CuratorFrameworkFactory.newClient(ZK_HOST, new ExponentialBackoffRetry(1000, 3));
    }

    private void createServiceDiscovery() {
        JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<InstanceDetails>(
                InstanceDetails.class);

        this.discovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class).client(this.curator).basePath("services")
                .serializer(serializer).build();
    }

    private void retrieveUserServices() throws Exception {
        final Collection<ServiceInstance<InstanceDetails>> services = discovery.queryForInstances("user");
        for (final ServiceInstance<InstanceDetails> service : services) {
            final String uri = service.buildUriSpec();

            System.out.println(uri);
            System.out.println("Description: " + service.getPayload().getDescription());
        }
    }

    private void initHazelcastDiscovery() {
        // either have hazelcast.xml in classpath or pass config file using -Dhazelcast.config
        HazelcastInstance instance = Hazelcast.newHazelcastInstance();
        com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap("user");

        Collection<String> values = multiMap.get("service");
        values.stream().forEach((it) -> System.out.println(it));
    }

    private void startHttpServer() {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        initializeRESTServices(router);

        server.requestHandler(router::accept).listen(port);
    }

    private void initializeRESTServices(Router router) {
        router.route().handler(BodyHandler.create());

        router.get("/services/blogs/:id").handler(routingContext -> {
            String id = routingContext.request().getParam("id");
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/html");
            response.end("Hello " + id + ". I am running blog service running on " + port);
        });

        router.post("/services/blogs/new").handler(routingContext -> {
            String body = routingContext.getBodyAsString();
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");
            response.end("{}");
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

        BlogApplication blogverticle = new BlogApplication();
        vertx.deployVerticle(blogverticle, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> result) {
                System.out.println("BlogApplication");
            }
        });
    }
}
