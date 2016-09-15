package discovery;

/**
 * Created by abhsinh2 on 15/09/16.
 */
public enum DiscoveryTypeEnum {
    ZOOKEEPER("zookeeper"), HAZELCAST("hazelcast"), VERTX("vertx");

    private String type;
    DiscoveryTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

}
