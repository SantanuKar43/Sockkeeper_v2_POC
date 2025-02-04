package org.sockkeeper.resources.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Path("/v1")
public class PublishResource {
    private final KafkaProducer<String, String> producer;
    private final CuratorFramework curator;

    @Inject
    public PublishResource(CuratorFramework curatorFramework, KafkaProducer<String, String> kafkaProducer) {
        this.producer = kafkaProducer;
        this.curator = curatorFramework;
    }

    @POST
    @Path("publish/{agentId}")
    public void publish(@PathParam("agentId") String agentId, String message) {
        try {
            log.info("publish request received for {}, message: {}", agentId, message);
            String topic = new String(curator.getData().forPath("/" + agentId), StandardCharsets.UTF_8);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
            producer.send(record);
        } catch (Exception e) {
            log.error("Exception occurred in publish", e);
            throw new RuntimeException(e);
        }
    }
}
