package org.sockkeeper.resources.v4;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SidelineConsumer implements MessageListener {
    private final PulsarClient pulsarClient;
    private final JedisPool jedisPool;

    public SidelineConsumer(PulsarClient pulsarClient, JedisPool jedisPool) {
        this.pulsarClient = pulsarClient;
        this.jedisPool = jedisPool;
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        // for each message, check KV store if there is a host for that agent
        // if yes, redirect message to that host's topic
        // no, nack with exponential backoff until message is too delayed.
        log.info("sideline consumer received");
        try {
            log.info("Message received from sideline: {}", new String(msg.getData()));
            String userId = msg.getKey();

            try (Jedis jedis = jedisPool.getResource()) {
                String userHost = jedis.get(Utils.getRedisKeyForUser(userId));
                if (userHost == null || userHost.isEmpty()) {
                    if (Instant.now().getEpochSecond() - msg.getEventTime() > 5*60) {
                        log.warn("couldn't find a host, dropping message {} for userId : {}", msg, userId);
                        consumer.acknowledge(msg);
                        return;
                    }
                    consumer.reconsumeLater(msg, 15, TimeUnit.SECONDS);
                    return;
                }

                log.info("passing message for user:{}, to present host:{}", userId, userHost);
                String topic = Utils.getTopicNameForHost(userHost);
                try (Producer<byte[]> producer = pulsarClient.newProducer().topic(topic).create()) {
                    producer.newMessage()
                            .key(userId)
                            .value(msg.getData())
                            .eventTime(msg.getEventTime())
                            .send();
                }
                consumer.acknowledge(msg);
            }
        } catch (Exception e) {
            log.error("Exception occurred in sidelineconsumer", e);
            consumer.negativeAcknowledge(msg);
        }
    }
}
