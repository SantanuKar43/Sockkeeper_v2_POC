package org.sockkeeper.bootstrap;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.sockkeeper.config.SockkeeperConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class SockkeeperModule extends AbstractModule {

    private final SockkeeperConfiguration configuration;
    private final Environment environment;

    public SockkeeperModule(SockkeeperConfiguration sockkeeperConfiguration, Environment environment) {
        this.configuration = sockkeeperConfiguration;
        this.environment = environment;
    }

    @Override
    public void configure() {
    }

    @Provides
    public ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Singleton
    @Provides
    public SockkeeperConfiguration getSockkeeperConfiguration() {
        return this.configuration;
    }

    @Singleton
    @Provides
    public CuratorFramework getCuratorFramework() {
        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString(configuration.getZk().getConnectionString())
                .sessionTimeoutMs(configuration.getZk().getSessionTimeout())
                .connectionTimeoutMs(configuration.getZk().getConnectionTimeout())
                .retryPolicy(new ExponentialBackoffRetry(configuration.getZk().getRetryBaseSleepTime(),
                        configuration.getZk().getMaxRetry()))
                .build();
        curator.start();
        return curator;
    }

    @Singleton
    @Provides
    public KafkaProducer<String, String> getKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getKafka().getServers());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(props);
    }

    @Singleton
    @Provides
    @Named("hostname")
    public String getHostName() throws UnknownHostException {
        if (configuration.getEnv().equals("local")) {
            return configuration.getLocalEnvHostname();
        }
        return InetAddress.getLocalHost().getHostName();
    }

    @Singleton
    @Provides
    @Named("sidelineTopic")
    public String getSidelineTopic() throws UnknownHostException {
        return configuration.getSidelineTopic();
    }

    @Singleton
    @Provides
    public MetricRegistry getMetricRegistry() {
        return environment.metrics();
    }

    @Singleton
    @Provides
    public JedisPool getJedisPool() {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(10);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTestOnBorrow(true);
        return new JedisPool(poolConfig,
                configuration.getRedis().getHost(),
                configuration.getRedis().getPort());
    }

    @Singleton
    @Provides
    public PulsarClient getPulsarClient() throws PulsarClientException {
        return PulsarClient.builder()
                .serviceUrl(configuration.getPulsar().getServiceUrl())
                .listenerThreads(Runtime.getRuntime().availableProcessors())
                .ioThreads(Runtime.getRuntime().availableProcessors())
                .build();
    }
}
