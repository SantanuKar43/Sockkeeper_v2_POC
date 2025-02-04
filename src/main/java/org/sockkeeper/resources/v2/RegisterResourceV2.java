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
 * 1 topic per agent, identified by agentId - agents might leave fk or new agents might join which will require topic creation/deletion.
 * Additional solve needed to manage agents.
 * */
@Slf4j
@ServerEndpoint("/v2/register/{agentId}")
public class RegisterResourceV2 {

    private final ConcurrentHashMap<String, Consumer> agentIdConsumerMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(500);
    private final SockkeeperConfiguration configuration;

    public RegisterResourceV2(SockkeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("agentId") String agentId) throws Exception {
        log.info("socket connection opened for: {}", agentId);
        session.setMaxIdleTimeout(-1);
        Properties properties = getConsumerProperties(agentId);
        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
        kafkaConsumer.subscribe(List.of("topic-" + agentId));
        Consumer consumer = new Consumer(kafkaConsumer, new AtomicBoolean(true), session);
        agentIdConsumerMap.put(agentId, consumer);
        executorService.submit(consumer);
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("agentId") String agentId) {
        log.info("message: {} , received on socket connection for: {}", message, agentId);
        //ignore
    }

    @OnClose
    public void onClose(Session session, @PathParam("agentId") String agentId) {
        log.info("socket connection closed for: {}", agentId);
        agentIdConsumerMap.get(agentId).stop();
        agentIdConsumerMap.remove(agentId);
    }

    private Properties getConsumerProperties(String agentId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getKafka().getServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-" + agentId);
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");  // Read from the earliest message if no offset exists
        return properties;
    }

}
