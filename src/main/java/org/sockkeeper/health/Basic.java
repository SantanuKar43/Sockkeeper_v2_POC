package org.sockkeeper.health;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Singleton;

@Singleton
public class Basic extends HealthCheck {

    @Override
    protected Result check() throws Exception {
        return Result.healthy("OK");
    }
}