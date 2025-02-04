package org.sockkeeper.resources.v3;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.zookeeper.CreateMode;
import org.sockkeeper.config.SockkeeperConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 1 topic per instance.
 * co-ordination required only during startup.
 * Instance to wait for free topic if none available during startup using watcher on free topics.
 * */
@Slf4j
@ServerEndpoint("/v3/register/{userId}")
public class RegisterResourceV3 implements CuratorCacheListener {
    private final ConcurrentHashMap<String, Session> userIdSessionMap = new ConcurrentHashMap<>();
    private final SockkeeperConfiguration configuration;
    private final CuratorFramework curator;
    private final String hostname;
    private final ExecutorService executorService = Executors.newWorkStealingPool();
    private final MetricRegistry metricRegistry;
    private AtomicReference<String> topicAssigned;

    @Inject
    public RegisterResourceV3(SockkeeperConfiguration configuration,
                              CuratorFramework curator,
                              @Named("hostname")String hostname,
                              MetricRegistry metricRegistry) {
        this.configuration = configuration;
        this.curator = curator;
        this.hostname = hostname;
        this.metricRegistry = metricRegistry;
        try {
            List<String> topics = curator.getChildren().forPath("/free");
            if (topics == null || topics.isEmpty()) {
                log.warn("Couldn't find a free topic");
                CuratorCache curatorCache = CuratorCache.build(curator, "/free");
                curatorCache.listenable().addListener(this);
                curatorCache.start();
                return;
            }
            for (String topic : topics) {
                try {
                    curator.create()
                            .withMode(CreateMode.EPHEMERAL)
                            .forPath("/locked/" + topic);
                    topicAssigned = new AtomicReference<>(topic);
                    Properties properties = getConsumerProperties();
                    KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
                    kafkaConsumer.subscribe(List.of(topic));
                    ConsumerV3 consumer = new ConsumerV3(userIdSessionMap, kafkaConsumer, metricRegistry);
                    executorService.submit(consumer);
                    return;
                } catch (Exception e) {
                    log.error("Error in locking topic {}, trying another", topic, e.getCause());
                }
            }
        } catch (Exception e) {
            log.error("Error while trying to find a free topic", e.getCause());
            CuratorCache curatorCache = CuratorCache.build(curator, "/free");
            curatorCache.listenable().addListener(this);
            curatorCache.start();
        }
    }

    private Properties getConsumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getKafka().getServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-" + hostname);
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");  // Read from the earliest message if no offset exists
        return properties;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) throws Exception {
        log.info("socket connection opened for: {}", userId);
        session.setMaxIdleTimeout(-1);
        curator.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath("/user/" + userId, topicAssigned.get().getBytes(StandardCharsets.UTF_8));
        userIdSessionMap.put(userId, session);
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("userId") String userId) {
        log.info("message: {} , received on socket connection for: {}", message, userId);
        //ignore
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        log.info("socket connection closed for: {}", userId);
        userIdSessionMap.remove(userId);
        try {
            curator.delete().guaranteed().forPath("/user/" + userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        log.info("Received event of type {}, old data {}, new data {}", type, oldData, data);
        if (Objects.requireNonNull(type) == Type.NODE_CREATED) {
            if ("/free".equals(data.getPath())) {
                return;
            }
            try {
                handleNodeCreated(data, PathChildrenCacheEvent.Type.CHILD_ADDED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleNodeCreated(ChildData data, PathChildrenCacheEvent.Type type) {
        if (topicAssigned == null) {
            String topic = new ArrayList<>(List.of(data.getPath().split("/"))).getLast();
            try {
                curator.create()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath("/locked/" + topic);
                topicAssigned = new AtomicReference<>(topic);
                Properties properties = getConsumerProperties();
                KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
                kafkaConsumer.subscribe(List.of(topic));
                ConsumerV3 consumer = new ConsumerV3(userIdSessionMap, kafkaConsumer, metricRegistry);
                executorService.submit(consumer);
            } catch (Exception e) {
                log.warn("couldn't lock topic {}, still waiting", topic, e);
            }
        }
    }
}
