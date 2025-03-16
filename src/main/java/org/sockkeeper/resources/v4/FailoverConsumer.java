package org.sockkeeper.resources.v4;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;

@Slf4j
public class FailoverConsumer implements MessageListener {
    private final Producer<byte[]> sidelineProducer;

    public FailoverConsumer(String sidelineTopic,
                            PulsarClient pulsarClient) throws PulsarClientException {
        sidelineProducer = pulsarClient.newProducer().topic(sidelineTopic).create();
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        log.info("failover consumer received started");
        try {
            log.info("failover consumer message received: {}", new String(msg.getData()));
            String userId = msg.getKey();
            String message = new String(msg.getData(), StandardCharsets.UTF_8);
            log.info("passing user:{}, message:{} to sideline", userId, message);

            sidelineProducer.newMessage()
                    .key(userId)
                    .value(message.getBytes(StandardCharsets.UTF_8))
                    .eventTime(msg.getEventTime() == 0 ? Instant.now().getEpochSecond() : msg.getEventTime())
                    .send();

            consumer.acknowledge(msg);
        } catch (Exception e) {
            log.error("Exception occurred in failover consumer", e);
            consumer.negativeAcknowledge(msg);
        }
    }
}
