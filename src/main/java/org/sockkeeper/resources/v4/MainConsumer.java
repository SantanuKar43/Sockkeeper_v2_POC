package org.sockkeeper.resources.v4;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.sockkeeper.config.SockkeeperConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * Copy of ConsumerV4
 */
@Slf4j
public class MainConsumer implements MessageListener {
    private final ConcurrentHashMap<String, Session> userIdSessionMap;
    private final MetricRegistry metricRegistry;
    private final JedisPool jedisPool;
    private final String hostname;
    private final Producer<byte[]> sidelineProducer;
    private final ObjectMapper objectMapper;
    private final long messageTTLInSeconds;
    private final long reconsumeDelayInSeconds;

    public MainConsumer(ConcurrentHashMap<String, Session> userIdSessionMap,
                        PulsarClient pulsarClient,
                        MetricRegistry metricRegistry,
                        JedisPool jedisPool,
                        String hostname, String sidelineTopic,
                        ObjectMapper objectMapper,
                        SockkeeperConfiguration configuration) throws PulsarClientException {
        this.userIdSessionMap = userIdSessionMap;
        this.metricRegistry = metricRegistry;
        this.jedisPool = jedisPool;
        this.hostname = hostname;
        this.objectMapper = objectMapper;
        this.sidelineProducer = pulsarClient
                .newProducer()
                .topic(sidelineTopic)
                .create();
        this.messageTTLInSeconds = configuration.getMessageTTLInSeconds();
        this.reconsumeDelayInSeconds = configuration.getMainReconsumeDelayTimeInSeconds();
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        log.info("main consumer received started");
        Timer.Context consumeV4Time = metricRegistry.timer("ConsumeV4Time").time();
        try {
            org.sockkeeper.core.Message message =
                    objectMapper.readValue(msg.getData(), org.sockkeeper.core.Message.class);
            log.info("main consumer message received: {}", message);
            String userId = message.destUserId();
            Session session = userIdSessionMap.get(message.destUserId());
            if (session != null && session.isOpen()) {
                session.getAsyncRemote().sendText(message.data());
                consumer.acknowledge(msg);
                return;
            }

            try (Jedis jedis = jedisPool.getResource()) {
                String userHost = jedis.get(Utils.getRedisKeyForUser(userId));
                if (hostname.equals(userHost)) {
                    // retry, should do with exponential backoff
                    if (Instant.now().getEpochSecond() - msg.getEventTime() < messageTTLInSeconds) {
                        log.info("user {} not online, will consume message later: {}", userId, message);
                        consumer.reconsumeLater(msg, reconsumeDelayInSeconds, TimeUnit.SECONDS);
                        return;
                    }
                }

                // Pass message to sideline topic for retry
                // Message ordering may break here.
                // Messages should have actual timestamp when event occurred.
                // Client UI can handle displaying messages in order based on its actual timestamp
                log.info("passing user:{}, message:{} to present host:{}", userId, message, userHost);

                sidelineProducer.newMessage()
                        .key(userId)
                        .value(msg.getData())
                        .eventTime(message.timestampEpochSecond())
                        .send();

                consumer.acknowledge(msg);
            }
        } catch (Exception e) {
            log.error("Exception occurred in main consumer", e);
            consumer.negativeAcknowledge(msg);
        } finally {
            consumeV4Time.close();
        }
    }
}
