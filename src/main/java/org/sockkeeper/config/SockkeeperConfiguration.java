package org.sockkeeper.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import lombok.Getter;

@Getter
public class SockkeeperConfiguration extends Configuration {
    private KafkaConfig kafka;
    private ZkConfig zk;
    private PulsarConfig pulsar;
    private RedisConfig redis;
    @JsonProperty("sideline-topic")
    private String sidelineTopic;
}
