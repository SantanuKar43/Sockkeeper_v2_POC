package org.sockkeeper.resources.v4;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.sockkeeper.config.SockkeeperConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SidelineConsumer implements MessageListener {
    private final PulsarClient pulsarClient;
    private final JedisPool jedisPool;
    private final Map<String, Producer<byte[]>> producerPool;
    private final ObjectMapper objectMapper;
    private final long sidelineTTLInSeconds;
    private final long reconsumeDelayInSeconds;
    private final String topicNamePrefix;

    public SidelineConsumer(PulsarClient pulsarClient,
                            JedisPool jedisPool,
                            ObjectMapper objectMapper,
                            SockkeeperConfiguration configuration) {
        this.pulsarClient = pulsarClient;
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.producerPool = new ConcurrentHashMap<>();
        this.sidelineTTLInSeconds = configuration.getSidelineTTLInSeconds();
        this.reconsumeDelayInSeconds = configuration.getSidelineReconsumeDelayTimeInSeconds();
        this.topicNamePrefix = configuration.getTopicNamePrefix();
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        // for each message, check KV store if there is a host for that agent
        // if yes, redirect message to that host's topic
        // no, nack with exponential backoff until message is too delayed.
        log.info("sideline consumer received");
        try {
            org.sockkeeper.core.Message message =
                    objectMapper.readValue(msg.getData(), org.sockkeeper.core.Message.class);
            log.info("Message received from sideline: {}", message);

            String userId = message.destUserId();

            try (Jedis jedis = jedisPool.getResource()) {
                String userHost = jedis.get(Utils.getRedisKeyForUser(userId));
                if (userHost == null || userHost.isEmpty()) {
                    if (Instant.now().getEpochSecond() - message.timestampEpochSecond() > sidelineTTLInSeconds) {
                        log.warn("couldn't find a host, dropping message {} for userId : {}", message, userId);
                        consumer.acknowledge(msg);
                        return;
                    }
                    consumer.reconsumeLater(msg, reconsumeDelayInSeconds, TimeUnit.SECONDS);
                    return;
                }

                String hostLiveness = jedis.get(Utils.getKeyForHostLiveness(userHost));
                if (hostLiveness == null) {
                    log.info("Host not live for user {}", userId);
                    if (Instant.now().getEpochSecond() - message.timestampEpochSecond() > sidelineTTLInSeconds) {
                        log.warn("couldn't find a host, dropping message {} for userId : {}", message, userId);
                        consumer.acknowledge(msg);
                        return;
                    }
                    consumer.reconsumeLater(msg, reconsumeDelayInSeconds, TimeUnit.SECONDS);
                    return;
                }

                log.info("passing message for user:{}, to present host:{}", userId, userHost);
                String topic = Utils.getTopicNameForHost(userHost, topicNamePrefix);
                producerPool.computeIfAbsent(topic, key -> {
                            try {
                                return pulsarClient.newProducer().topic(topic).create();
                            } catch (PulsarClientException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .newMessage()
                        .key(userId)
                        .value(msg.getData())
                        .eventTime(message.timestampEpochSecond()) // eventTime is reset on reconsumeLater. This will not be problem in prod as the event time will be part of the message payload
                        .send();

                consumer.acknowledge(msg);
            }
        } catch (Exception e) {
            log.error("Exception occurred in sidelineconsumer", e);
            consumer.negativeAcknowledge(msg);
        }
    }
}
