package org.sockkeeper.resources.v4;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.sockkeeper.config.SockkeeperConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 1 topic per instance, linked to hostname.
 * 0 coordination approach.
 * No ephemeral nodes.
 * Intermittent disconnects are handled via internode communication.
 * Requires stable ordered hostnames,
 * easily achievable by k8s <a href="https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/">StatefulSets</a>
 * Prone to out of order messages during disconnects, UI to handle ordering.
 * */
@Slf4j
@ServerEndpoint("/v4/register/{userId}")
public class RegisterResourceV4 {

    private final ConcurrentHashMap<String, Session> userIdSessionMap = new ConcurrentHashMap<>();
    private final SockkeeperConfiguration configuration;
    private final String hostname;
    private final MetricRegistry metricRegistry;
    private final JedisPool jedisPool;
    private AtomicReference<String> topicAssigned;

    @Inject
    public RegisterResourceV4(SockkeeperConfiguration configuration,
                              @Named("hostname")String hostname,
                              MetricRegistry metricRegistry,
                              JedisPool jedisPool,
                              PulsarClient pulsarClient) throws PulsarClientException {
        this.configuration = configuration;
        this.hostname = hostname;
        this.metricRegistry = metricRegistry;
        this.topicAssigned = new AtomicReference<>(Utils.getTopicNameForHost(hostname));
        this.jedisPool = jedisPool;
        try {
            MessageListener myMessageListener
                    = new ConsumerV4(userIdSessionMap,
                            pulsarClient,
                            metricRegistry,
                            jedisPool,
                            hostname);
            pulsarClient.newConsumer()
                    .topic(topicAssigned.get())
                    .subscriptionName(Utils.getSubscriptionNameForHost(hostname))
                    .messageListener(myMessageListener)
                    .subscribe();
        } catch (Exception e) {
            log.error("Error in starting consumer for topic {}", topicAssigned, e.getCause());
            throw e;
        }
    }


    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) throws Exception {
        Timer.Context onOpen = metricRegistry.timer("onOpen").time();
        log.info("socket connection opened for: {}", userId);
        session.setMaxIdleTimeout(-1);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(Utils.getRedisKeyForUser(userId), hostname);
        }
        userIdSessionMap.put(userId, session);
        onOpen.close();
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("userId") String userId) {
        log.info("message: {} , received on socket connection for: {}", message, userId);
        //ignore
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) throws IOException {
        Timer.Context onClose = metricRegistry.timer("onClose").time();
        log.info("socket connection closed for: {}", userId);
        userIdSessionMap.remove(userId);
        onClose.close();
    }
}
