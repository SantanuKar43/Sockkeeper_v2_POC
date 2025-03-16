package org.sockkeeper.health;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class BasicHealth extends HealthCheck {

    private final AtomicBoolean isSidelineConsumerStarted;
    private final AtomicBoolean isFailoverConsumerStarted;
    private final AtomicBoolean isLivenessJobStarted;

    public BasicHealth() {
        this.isSidelineConsumerStarted = new AtomicBoolean(false);
        this.isFailoverConsumerStarted = new AtomicBoolean(false);;
        this.isLivenessJobStarted = new AtomicBoolean(false);;
    }

    @Override
    protected Result check() throws Exception {
        if (!isSidelineConsumerStarted.get()) {
            return Result.unhealthy("sideline consumer not started yet!");
        }
        if (!isFailoverConsumerStarted.get()) {
            return Result.unhealthy("failover consumer not started yet!");
        }
        if (!isLivenessJobStarted.get()) {
            return Result.unhealthy("liveness job not started yet!");
        }
        return Result.healthy("OK");
    }

    public void markSidelineConsumerStarted() {
        isSidelineConsumerStarted.set(true);
    }

    public void markFailoverConsumerStarted() {
        isFailoverConsumerStarted.set(true);
    }

    public void markLivenessJobStarted(boolean started) {
        isLivenessJobStarted.set(started);
    }
}