package org.sockkeeper.resources.v3;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConsumerV3 implements Runnable {
    private final ConcurrentHashMap<String, Session> agentIdSessionMap;
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final MetricRegistry metricRegistry;

    public ConsumerV3(ConcurrentHashMap<String, Session> agentIdSessionMap, KafkaConsumer<String, String> kafkaConsumer, MetricRegistry metricRegistry) {
        this.agentIdSessionMap = agentIdSessionMap;
        this.kafkaConsumer = kafkaConsumer;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        try {
            log.info("consumer started");
            Timer consumeV3Timer = metricRegistry.timer("ConsumeV3Time");
            while (true) {
                kafkaConsumer.poll(Duration.ofMillis(2000)).forEach(record -> {
                    Timer.Context consumeV3Time = consumeV3Timer.time();
                    Session session = agentIdSessionMap.get(record.key());
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(record.value());
                    }
                    log.info("Received message: {} from topic: {}", record.value(), record.topic());
                    consumeV3Time.close();
                });
            }
        } catch (Exception e) {
            log.error("error occurred in consumer: ", e);
            throw new RuntimeException(e);
        } finally {
            log.info("stopping consumer");
            kafkaConsumer.close();
        }
    }
}
