package org.sockkeeper.resources.v4;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConsumerV4 implements MessageListener {
    private final ConcurrentHashMap<String, Session> userIdSessionMap;
    private final PulsarClient pulsarClient;
    private final MetricRegistry metricRegistry;
    private final JedisPool jedisPool;
    private final String hostname;
    private final String sidelineTopic;

    public ConsumerV4(ConcurrentHashMap<String, Session> userIdSessionMap,
                      PulsarClient pulsarClient,
                      MetricRegistry metricRegistry,
                      JedisPool jedisPool,
                      String hostname, String sidelineTopic) {
        this.userIdSessionMap = userIdSessionMap;
        this.pulsarClient = pulsarClient;
        this.metricRegistry = metricRegistry;
        this.jedisPool = jedisPool;
        this.hostname = hostname;
        this.sidelineTopic = sidelineTopic;
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        log.info("received started");
        Timer.Context consumeV4Time = metricRegistry.timer("ConsumeV4Time").time();
        try {
            log.info("Message received: {}", new String(msg.getData()));
            String userId = msg.getKey();
            String message = new String(msg.getData(), StandardCharsets.UTF_8);
            Session session = userIdSessionMap.get(userId);
            if (session != null && session.isOpen()) {
                session.getAsyncRemote().sendText(message);
                consumer.acknowledge(msg);
                return;
            }

            try (Jedis jedis = jedisPool.getResource()) {
                String userHost = jedis.get(Utils.getRedisKeyForUser(userId));
                if (hostname.equals(userHost)) {
                    // retry, should do with exponential backoff
                    if (Instant.now().getEpochSecond() - msg.getEventTime() < 45) {
                        log.info("user {} not online, will consume message later: {}", userId, message);
                        consumer.reconsumeLater(msg, 15, TimeUnit.SECONDS);
                        return;
                    }
                }

                // Pass message to sideline topic for retry
                // Message ordering may break here.
                // Messages should have actual timestamp when event occurred.
                // Client UI can handle displaying messages in order based on its actual timestamp
                log.info("passing user:{}, message:{} to present host:{}", userId, message, userHost);
                String topic = sidelineTopic;
                try (Producer<byte[]> producer = pulsarClient.newProducer().topic(topic).create()) {
                    producer.newMessage()
                            .key(userId)
                            .value(message.getBytes(StandardCharsets.UTF_8))
                            .eventTime(msg.getEventTime())
                            .send();
                }
                consumer.acknowledge(msg);
            }
        } catch (Exception e) {
            consumer.negativeAcknowledge(msg);
        } finally {
            consumeV4Time.close();
        }
    }
}
