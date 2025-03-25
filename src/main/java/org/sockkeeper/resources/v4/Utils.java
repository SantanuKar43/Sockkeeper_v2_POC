package org.sockkeeper.resources.v4;

import java.util.UUID;

public final class Utils {

    private Utils() {
    }

    public static String getTopicNameForHost(String hostname, String topicNamePrefix) {
        int partition = getPartition(hostname);
        return getTopicName(topicNamePrefix, partition);
    }

    public static String getTopicName(String topicNamePrefix, int partition) {
        return topicNamePrefix + "-partition-" + partition;
    }

    public static int getPartition(String hostname) {
        // last token of hostname is used as partition id
        String[] split = hostname.split("-");
        return Integer.parseInt(split[split.length - 1]);
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
