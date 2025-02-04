package org.sockkeeper.resources.v2;

import com.google.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2")
public class PublishResourceV2 {
    private final KafkaProducer<String, String> producer;

    @Inject
    public PublishResourceV2(KafkaProducer<String, String> kafkaProducer) {
        this.producer = kafkaProducer;
    }

    @POST
    @Path("publish/{userId}")
    public void publish(@PathParam("userId") String userId, String message) {
        try {
            log.info("publish request received for {}, message: {}", userId, message);
            String topic = "topic-" + userId;
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
            producer.send(record);
        } catch (Exception e) {
            log.error("Exception occurred in publish", e);
            throw new RuntimeException(e);
        }
    }
}
