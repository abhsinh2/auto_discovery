package application;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.function.Consumer;

/**
 * Created by abhsinh2 on 16/09/16.
 */
public class Main extends AbstractVerticle {

    private static String deploymentID;
    private static int port = 0;
    private static String discoveryTypeStr;

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        } else if (args.length == 2) {
            port = Integer.parseInt(args[0]);
            discoveryTypeStr =  args[1];
        }

        DeploymentOptions deploymentOptions = new DeploymentOptions();

        Consumer<Vertx> runner = vertx -> {
            try {
                vertx.deployVerticle(Main.class.getName(), deploymentOptions);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };

        VertxOptions vertxOptions = new VertxOptions().setWorkerPoolSize(10);
        Vertx vertx = Vertx.vertx(vertxOptions);
        runner.accept(vertx);
    }

    @Override
    public void start() throws Exception {
        BlogApplication userverticle = new BlogApplication(port, discoveryTypeStr);

        vertx.deployVerticle(userverticle, result -> {
            System.out.println("User Application deployed");
            deploymentID = result.result();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Exiting Blog application");
                vertx.undeploy(deploymentID, res -> {
                    if (res.succeeded()) {
                        System.out.println("Undeployed ok");
                    } else {
                        System.out.println("Undeploy failed!");
                    }
                });
            }
        }));
    }
}
