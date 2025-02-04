package org.sockkeeper.resources.v3;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Path("/v3")
public class PublishResourceV3 {
    private final KafkaProducer<String, String> producer;
    private final CuratorFramework curator;

    @Inject
    public PublishResourceV3(KafkaProducer<String, String> producer, CuratorFramework curator) {
        this.producer = producer;
        this.curator = curator;
    }

    @POST
    @Path("publish/{agentId}")
    @Timed(name = "publish")
    public void publish(@PathParam("agentId") String agentId, String message) {
        try {
            log.info("publish request received for {}, message: {}", agentId, message);
            String topic = new String(curator.getData().forPath("/agent/" + agentId), StandardCharsets.UTF_8);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, agentId, message);
            producer.send(record);
        } catch (Exception e) {
            log.error("Exception occurred in publish", e);
            throw new RuntimeException(e);
        }
    }
}
