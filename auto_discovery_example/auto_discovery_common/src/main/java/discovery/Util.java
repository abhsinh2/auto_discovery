package discovery;

/**
 * Created by abhsinh2 on 15/09/16.
 */
public class Util {

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
