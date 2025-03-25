package org.sockkeeper.resources.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void getTopicNameForHost() {
        assertEquals("topic-pref-partition-0", Utils.getTopicNameForHost("host-name-0", "topic-pref"));
    }

    @Test
    void getRedisKeyForUser() {
        assertEquals("user:userId", Utils.getRedisKeyForUser("userId"));
    }

}