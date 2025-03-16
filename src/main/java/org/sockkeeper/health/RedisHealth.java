package org.sockkeeper.health;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisHealth extends HealthCheck {

    private final JedisPool jedisPool;

    @Inject
    public RedisHealth(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    protected Result check() throws Exception {
        try(Jedis jedis = jedisPool.getResource()) {
            if (jedis.isConnected()) {
                return Result.healthy("connected");
            } else {
                return Result.unhealthy("redis not connected");
            }
        } catch (Exception e) {
            return Result.unhealthy(e.getMessage());
        }
    }
}
