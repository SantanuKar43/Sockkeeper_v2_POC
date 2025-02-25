package org.sockkeeper.resources.v4;

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

}
