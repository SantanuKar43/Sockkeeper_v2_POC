package org.sockkeeper.resources.v4;

import java.util.UUID;

public final class Utils {

    private Utils() {
    }

    public static String getTopicNameForHost(String hostname) {
        return hostname + "-topic";
    }

    public static String getSubscriptionName() {
        return "sock-subscription";
    }

    public static String getRedisKeyForUser(String userId) {
        return "user:" + userId;
    }

    public static String getKeyForHostLiveness(String hostname) {
        return "host:" + hostname;
    }

    public static String getMainConsumerName(String hostname) {
        return "main-" + hostname;
    }

    public static String getFailoverConsumerName(String hostname) {
        return "failover-" + hostname;
    }

    public static String getSidelineConsumerName(String hostname) {
        return "sideline-" + hostname;
    }

    public static String getUniqueId() {
        return UUID.randomUUID().toString();
    }
}
