package org.sockkeeper.resources.v4;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;

@Slf4j
public class FailoverConsumer implements MessageListener {
    private final Producer<byte[]> sidelineProducer;
    private final ObjectMapper objectMapper;

    public FailoverConsumer(String sidelineTopic,
                            PulsarClient pulsarClient, ObjectMapper objectMapper) throws PulsarClientException {
        this.objectMapper = objectMapper;
        sidelineProducer = pulsarClient.newProducer().topic(sidelineTopic).create();
    }

    @Override
    public void received(Consumer consumer, Message msg) {
        log.info("failover consumer received started");
        try {
            org.sockkeeper.core.Message message =
                    objectMapper.readValue(msg.getData(), org.sockkeeper.core.Message.class);
            log.info("failover consumer message received: {}", new String(msg.getData()));
            String userId = message.destUserId();
            log.info("passing user:{}, message:{} to sideline", userId, message);

            sidelineProducer.newMessage()
                    .key(userId)
                    .value(msg.getData())
                    .eventTime(message.timestampEpochSecond())
                    .send();

            consumer.acknowledge(msg);
        } catch (Exception e) {
            log.error("Exception occurred in failover consumer", e);
            consumer.negativeAcknowledge(msg);
        }
    }
}
