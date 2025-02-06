package org.sockkeeper.resources.v4;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Path("/v4")
public class PublishResourceV4 {

    private final PulsarClient pulsarClient;
    private final JedisPool jedisPool;

    @Inject
    public PublishResourceV4(PulsarClient pulsarClient, JedisPool jedisPool) {
        this.pulsarClient = pulsarClient;
        this.jedisPool = jedisPool;
    }

    @POST
    @Path("publish/{userId}")
    @Timed(name = "publish")
    public void publish(@PathParam("userId") String userId, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            log.info("publish request received for {}, message: {}", userId, message);
            String userHost = jedis.get(Utils.getRedisKeyForUser(userId));
            if (userHost == null) {
                log.info("Topic not found for user {}", userId);
                return;
            }
            try (Producer<byte[]> producer = pulsarClient.newProducer().topic(userHost + "-topic").create()) {
                producer.newMessage()
                        .key(userId)
                        .value(message.getBytes(StandardCharsets.UTF_8))
                        .eventTime(Instant.now().getEpochSecond())
                        .send();
            }
        } catch (Exception e) {
            log.error("Exception occurred in publish", e);
            throw new RuntimeException(e);
        }
    }
}
