package org.sockkeeper.resources.v1;

import com.google.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.zookeeper.CreateMode;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.resources.Consumer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dynamic topic allocation to user on connection and de-allocation on disconnect.
 * Complex co-ordination logic involving distributed locks.
 * Too many topics
 * */
@Slf4j
@ServerEndpoint("/v1/register/{userId}")
public class RegisterResource implements CuratorCacheListener {

    private static final String FREE_TOPICS_PATH = "/free";
    private static final String LOCKED_TOPICS_PATH = "/locked";
    private final Map<String, AtomicBoolean> userConnectionStatusMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(500);
    private final CuratorFramework curator;
    private final SockkeeperConfiguration configuration;

    @Inject
    public RegisterResource(CuratorFramework curatorFramework, SockkeeperConfiguration configuration) throws Exception {
        this.curator = curatorFramework;
        this.configuration = configuration;
        CuratorCache curatorCache = CuratorCache.build(curator, LOCKED_TOPICS_PATH);
        curatorCache.listenable().addListener(this);
        curatorCache.start();
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) throws Exception {
        log.info("socket connection opened for: {}", userId);
        session.setMaxIdleTimeout(-1);
        String freeTopic = getFreeTopic();
        if (freeTopic == null || freeTopic.isEmpty()) {
            throw new RuntimeException("Unable to find a free topic");
        }
        try {
            curator.create()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath("/" + userId, freeTopic.getBytes(StandardCharsets.UTF_8));
            userConnectionStatusMap.computeIfAbsent(userId,
                    key -> new AtomicBoolean()).set(true);

            Properties properties = getConsumerProperties(userId);
            KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
            kafkaConsumer.subscribe(List.of(freeTopic));
            Consumer consumer = new Consumer(kafkaConsumer, userConnectionStatusMap.get(userId), session);
            executorService.submit(consumer);
        } catch (Exception e) {
            log.error("Exception occurred on open:", e);
            curator.delete().idempotent().forPath("/" + userId);
            curator.delete().idempotent().forPath(LOCKED_TOPICS_PATH + "/" + freeTopic);
            userConnectionStatusMap.get(userId).set(false);
            userConnectionStatusMap.remove(userId);
        }
    }

    private Properties getConsumerProperties(String userId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getKafka().getServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-" + userId);
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");  // Read from the earliest message if no offset exists
        return properties;
    }

    private String getFreeTopic() {
        // needs to be done with transactions to handle race conditions.
        try {
            List<String> freeTopics = curator.getChildren().forPath(FREE_TOPICS_PATH);
            if (freeTopics.isEmpty()) {
                log.info("No free topics available");
                return null;
            }
            String topicId = freeTopics.getFirst();
            String lockPath = LOCKED_TOPICS_PATH + "/" + topicId;

            curator.create()
                    .creatingParentsIfNeeded()
                    .withMode(org.apache.zookeeper.CreateMode.EPHEMERAL)
                    .forPath(lockPath, topicId.getBytes(StandardCharsets.UTF_8));

            curator.delete().forPath(FREE_TOPICS_PATH + "/" + topicId);

            log.info("Acquired lock on topic: {}", topicId);
            return topicId;
        } catch (Exception e) {
            log.error("Error acquiring topic", e);
            throw new RuntimeException("Error acquiring topic", e);
        }
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("userId") String userId) {
        log.info("message: {} , received on socket connection for: {}", message, userId);
        // ignored
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        try {
            log.info("socket connection closed for: {}", userId);
            userConnectionStatusMap.getOrDefault(userId, new AtomicBoolean()).set(false);
            userConnectionStatusMap.remove(userId);
            String topic = new String(curator.getData().forPath("/" + userId), StandardCharsets.UTF_8);
            curator.delete().forPath("/" + userId);
            curator.delete().forPath(LOCKED_TOPICS_PATH + "/" + topic);
        } catch (Exception e) {
            log.error("Error occurred in closing session:", e);
            throw new RuntimeException(e);
        }
    }

    public void handleNodeDelete(ChildData oldData, PathChildrenCacheEvent.Type type) throws Exception {
        // multiple instances will receive this event and try to create new node.
        if (Objects.requireNonNull(type)
                == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
            log.info("Child node removed: {}", oldData.getPath());
            curator.create()
                    .idempotent()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(FREE_TOPICS_PATH + "/" + new String(oldData.getData(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        if (Objects.requireNonNull(type) == Type.NODE_DELETED) {
            if (LOCKED_TOPICS_PATH.equals(oldData.getPath())) {
                return;
            }
            try {
                handleNodeDelete(oldData, PathChildrenCacheEvent.Type.CHILD_REMOVED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}