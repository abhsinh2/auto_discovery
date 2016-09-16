package discovery;

/**
 * Created by abhsinh2 on 15/09/16.
 */
public class Util {

    public static final String zk_basePath = "services";
    public static final String zk_usernode = "user";

    public static final String hz_userMapName = "user";
    public static final String hz_userServiceKey = "service";

    public static final String vertx_discoveryAnnounceAddress = "user-service-announce";
    public static final String vertx_discoveryName = "user-name";
    public static final String vertx_discoveryServiceName = "user-service";
    public static final String vertx_discoveryServiceKey = "endpoint";

    public static DiscoveryTypeEnum getDiscoveryEnum(String discoveryTypeStr) {
        DiscoveryTypeEnum discoveryType;
        if (discoveryTypeStr.equalsIgnoreCase(DiscoveryTypeEnum.ZOOKEEPER.getType())) {
            discoveryType = DiscoveryTypeEnum.ZOOKEEPER;
        } else if (discoveryTypeStr.equalsIgnoreCase(DiscoveryTypeEnum.HAZELCAST.getType())) {
            discoveryType = DiscoveryTypeEnum.HAZELCAST;
        } else if (discoveryTypeStr.equalsIgnoreCase(DiscoveryTypeEnum.VERTX.getType())) {
            discoveryType = DiscoveryTypeEnum.VERTX;
        } else {
            throw new UnsupportedOperationException("Discovery type " + discoveryTypeStr + " not supported");
        }

        return discoveryType;
    }
}
