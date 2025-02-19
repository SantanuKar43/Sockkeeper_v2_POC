package org.sockkeeper.resources.v4;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BackupJob implements Runnable {
    private final PulsarClient pulsarClient;
    private final JedisPool jedisPool;
    private final String sidelineTopicName;

    public BackupJob(PulsarClient pulsarClient, JedisPool jedisPool, String sidelineTopicName) throws PulsarClientException {
        this.pulsarClient = pulsarClient;
        this.jedisPool = jedisPool;
        this.sidelineTopicName = sidelineTopicName;
    }

    @Override
    public void run() {
        // get all topics from KV store
        // for each topic, create an exclusive consumer
        // if consumer created successfully, consume for 2 mins and move messages to sideline topic
        // close consumer
        log.info("started backup job");
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> allHosts = jedis.smembers("all-hosts");
            Producer<byte[]> sidelineProducer = pulsarClient.newProducer()
                    .topic(sidelineTopicName)
                    .create();
            for (String host : allHosts) {
                String topic = Utils.getTopicNameForHost(host);
                try {
                    Consumer<byte[]> backupConsumer = pulsarClient.newConsumer()
                            .topic(topic)
                            .subscriptionType(SubscriptionType.Exclusive)
                            .subscriptionName(Utils.getSubscriptionNameForHost(host))
                            .batchReceivePolicy(BatchReceivePolicy. builder()
                                    .maxNumMessages(25)
                                    .timeout(10, TimeUnit.SECONDS)
                                    .build())
                            .subscribe();

                    log.info("created backup consumer for topic {}", topic);

                    Instant twoMinFromNow = Instant.now().plusSeconds(2*60);
                    while (Instant.now().isBefore(twoMinFromNow)) {
                        Messages<byte[]> messages = backupConsumer.batchReceive();
                        for (Message<byte[]> msg : messages) {
                            log.info("publishing message {} for user {} to sideline", msg.getData(), msg.getKey());
                            sidelineProducer.newMessage()
                                    .key(msg.getKey())
                                    .value(msg.getData())
                                    .eventTime(msg.getEventTime())
                                    .send();
                            backupConsumer.acknowledge(msg);
                        }
                    }
                    backupConsumer.close();

                } catch (PulsarClientException pulsarClientException) {
                    log.warn("Unable to subscribe to topic: {}, trying another", topic);
                }
            }
        } catch (PulsarClientException e) {
            throw new RuntimeException(e);
        }
    }
}
