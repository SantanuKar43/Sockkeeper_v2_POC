package org.sockkeeper.resources.v4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void getTopicNameForHost() {
        assertEquals("hostname-topic", Utils.getTopicNameForHost("hostname"));
    }

    @Test
    void getRedisKeyForUser() {
        assertEquals("user:userId", Utils.getRedisKeyForUser("userId"));
    }

}