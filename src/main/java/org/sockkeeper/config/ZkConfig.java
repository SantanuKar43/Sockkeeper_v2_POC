package org.sockkeeper.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZkConfig {
    private String connectionString;
    private int connectionTimeout;
    private int sessionTimeout;
    private int retryBaseSleepTime;
    private int maxRetry;
}
