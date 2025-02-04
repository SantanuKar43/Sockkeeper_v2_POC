package org.sockkeeper.config;

import io.dropwizard.core.Configuration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SockkeeperConfiguration extends Configuration {
    private KafkaConfig kafka;
    private ZkConfig zk;
}
