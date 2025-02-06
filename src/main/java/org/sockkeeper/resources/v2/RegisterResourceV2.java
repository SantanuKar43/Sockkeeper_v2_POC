package org.sockkeeper.resources.v2;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.resources.Consumer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 1 topic per user, identified by userId - users might leave fk or new users might join which will require topic creation/deletion.
 * Additional solve needed to manage users.
 * too many topics
 * */
@Slf4j
@ServerEndpoint("/v2/register/{userId}")
public class RegisterResourceV2 {

    private final ConcurrentHashMap<String, Consumer> userIdConsumerMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(500);
    private final SockkeeperConfiguration configuration;

    public RegisterResourceV2(SockkeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) throws Exception {
        log.info("socket connection opened for: {}", userId);
        session.setMaxIdleTimeout(-1);
        Properties properties = getConsumerProperties(userId);
        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
        kafkaConsumer.subscribe(List.of("topic-" + userId));
        Consumer consumer = new Consumer(kafkaConsumer, new AtomicBoolean(true), session);
        userIdConsumerMap.put(userId, consumer);
        executorService.submit(consumer);
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("userId") String userId) {
        log.info("message: {} , received on socket connection for: {}", message, userId);
        //ignore
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        log.info("socket connection closed for: {}", userId);
        userIdConsumerMap.get(userId).stop();
        userIdConsumerMap.remove(userId);
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

}
