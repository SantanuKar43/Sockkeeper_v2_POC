package org.sockkeeper.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import java.util.List;
import lombok.Getter;

@Getter
public class SockkeeperConfiguration extends Configuration {
    private String env;
    private String localEnvHostname;
    private long hostLivenessTTLInSeconds;
    private long userHeartbeatTTLInSeconds;
    private long messageTTLInSeconds;
    private long sidelineTTLInSeconds;
    private long mainReconsumeDelayTimeInSeconds;
    private long sidelineReconsumeDelayTimeInSeconds;
    private long hostLivenessPeriodInSeconds;
    private long hostLivenessInitialDelayInSeconds;
    private String topicNamePrefix;
    private int topicPartitions;
    private KafkaConfig kafka;
    private ZkConfig zk;
    private PulsarConfig pulsar;
    private RedisConfig redis;
    @JsonProperty("sideline-topic")
    private String sidelineTopic;
}
