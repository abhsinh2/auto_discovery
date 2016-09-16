package application;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import discovery.DiscoveryTypeEnum;
import discovery.InstanceDetails;
import discovery.Util;
import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.ServiceReference;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.Collection;
import java.util.List;

/**
 * abhsinh2
 */
public class BlogApplication extends AbstractVerticle {

    private static DiscoveryTypeEnum discoveryType = DiscoveryTypeEnum.ZOOKEEPER;

    // Zookeeper variables and constants
    private static final String ZK_HOST = "localhost:2181";
    private CuratorFramework curator;
    private ServiceDiscovery<InstanceDetails> zkDiscovery;
    private io.vertx.servicediscovery.ServiceDiscovery vertxServiceDiscovery;

    private int port = 0;
    private String discoveryTypeStr;

    public BlogApplication(int port, String discoveryTypeStr) {
        this.port = port;
        this.discoveryTypeStr = discoveryTypeStr;
    }

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

        retrieveUserServices();
    }

    public void createCurator() {
        this.curator = CuratorFrameworkFactory.newClient(ZK_HOST, new ExponentialBackoffRetry(1000, 3));
    }

    private void createServiceDiscovery() {
        JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<InstanceDetails>(
                InstanceDetails.class);

        this.zkDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class).client(this.curator).basePath(Util.zk_basePath)
                .serializer(serializer).build();
    }

    private void retrieveUserServices() throws Exception {
        final Collection<ServiceInstance<InstanceDetails>> services = zkDiscovery.queryForInstances(Util.zk_usernode);
        for (final ServiceInstance<InstanceDetails> service : services) {
            final String uri = service.buildUriSpec();

            System.out.println("User instance:" + uri);
            System.out.println("Description: " + service.getPayload().getDescription());
        }
    }

    private void initHazelcastDiscovery() {
        Config config = new Config();
        // either have hazelcast.xml in classpath or pass config file using -Dhazelcast.config
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        com.hazelcast.core.MultiMap<String, String> multiMap = instance.getMultiMap(Util.hz_userMapName);

        Collection<String> values = multiMap.get(Util.hz_userServiceKey);
        values.stream().forEach((it) -> System.out.println("User Instance: " + it));
    }

    private void initVertxServiceDiscovery() {
        //vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx);
        vertxServiceDiscovery = io.vertx.servicediscovery.ServiceDiscovery.create(vertx,
                new ServiceDiscoveryOptions().setAnnounceAddress(Util.vertx_discoveryAnnounceAddress)
                        .setName(Util.vertx_discoveryName));

        vertxServiceDiscovery.getRecord(r -> true, ar -> {
            if (ar.succeeded()) {
                Record record = ar.result();
                if (record != null) {
                    System.out.println("we have a record:" + record.getLocation().getString(Util.vertx_discoveryServiceKey));
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });


        vertxServiceDiscovery.getRecord((JsonObject) null, ar -> {
            if (ar.succeeded()) {
                Record record = ar.result();
                if (record != null) {
                    System.out.println("we have a record:" + record.getLocation().getString(Util.vertx_discoveryServiceKey));
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });


        // Get a record by name
        vertxServiceDiscovery.getRecord(r -> r.getName().equals(Util.vertx_discoveryServiceName), ar -> {
            if (ar.succeeded()) {
                Record record = ar.result();
                if (record != null) {
                    System.out.println("we have a record:" + record.getLocation().getString(Util.vertx_discoveryServiceKey));

                    // Retrieve the service reference
                    ServiceReference reference = vertxServiceDiscovery.getReference(record);

                    // Retrieve the service object
                    HttpClient client = reference.get();

                    // You need to path the complete path
                    client.getNow("/services/users/abhi", response -> {
                        System.out.println("Got Resonse Code:" + response.statusCode());

                        // Dont' forget to release the service
                        reference.release();
                    });
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });


        vertxServiceDiscovery.getRecord(new JsonObject().put("name", Util.vertx_discoveryServiceName), ar -> {
            if (ar.succeeded()) {
                Record record = ar.result();
                if (record != null) {
                    System.out.println("we have a record:" + record.getLocation().getString(Util.vertx_discoveryServiceKey));
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });


        // Get all records matching the filter
        vertxServiceDiscovery.getRecords(r -> "some-value".equals(r.getMetadata().getString("some-label")), ar -> {
            if (ar.succeeded()) {
                List<Record> results = ar.result();
                if (results != null && results.size() > 0) {
                    System.out.println("we have records:" + results);
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });

        vertxServiceDiscovery.getRecords(new JsonObject().put("some-label", "some-value"), ar -> {
            if (ar.succeeded()) {
                List<Record> results = ar.result();
                if (results != null && results.size() > 0) {
                    System.out.println("we have records:" + results);
                } else {
                    System.out.println("the lookup succeeded, but no matching service");
                }
            } else {
                System.out.println("lookup failed");
            }
        });
    }

    private void startHttpServer() {
        System.out.println("Starting Server ");
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        initializeRESTServices(router);

        System.out.println("Listening to " + port);
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

        if (this.vertxServiceDiscovery != null)
            this.vertxServiceDiscovery.close();

        if (this.zkDiscovery != null)
            this.zkDiscovery.close();

        if (this.curator != null)
            this.curator.close();

        //CloseableUtils.closeQuietly(this.zkDiscovery);
        //CloseableUtils.closeQuietly(this.curator);

        stopFuture.complete();
    }
}
