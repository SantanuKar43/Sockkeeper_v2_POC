package org.sockkeeper.resources;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Deprecated
public class Consumer implements Runnable {

    private final KafkaConsumer<String, String> kafkaConsumer;
    private final AtomicBoolean isRunning;
    private final Session session;


    public Consumer(KafkaConsumer<String, String> kafkaConsumer, AtomicBoolean isRunning, Session session) {
        this.kafkaConsumer = kafkaConsumer;
        this.isRunning = isRunning;
        this.session = session;
    }

    @Override
    public void run() {
        try {
            log.info("consumer started for session {}", session);
            while (isRunning.get()) {
                kafkaConsumer.poll(Duration.ofMillis(2000)).forEach(record -> {
                    try {
                        if (session.isOpen()) {
                            session.getBasicRemote().sendText(record.value());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("Received message: {} from topic: {}", record.value(), record.topic());
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

    public void stop() {
        this.isRunning.set(false);
    }
}
