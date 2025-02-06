package org.sockkeeper.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RedisConfig {
    private String host;
    private int port;
}
