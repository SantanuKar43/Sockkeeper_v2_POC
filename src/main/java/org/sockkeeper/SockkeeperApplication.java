package org.sockkeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.sockkeeper.bootstrap.SockkeeperModule;
import org.sockkeeper.bootstrap.WebSocketConfigurator;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.health.BasicHealth;
import org.sockkeeper.health.RedisHealth;
import org.sockkeeper.resources.v4.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SockkeeperApplication extends Application<SockkeeperConfiguration> {

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static void main(final String[] args) throws Exception {
        new SockkeeperApplication().run(args);
    }

    @Override
    public String getName() {
        return "Sockkeeper";
    }

    @Override
    public void initialize(final Bootstrap<SockkeeperConfiguration> bootstrap) {
        // TODO: application initialization
    }

    @Override
    public void run(final SockkeeperConfiguration configuration,
                    final Environment environment) throws PulsarClientException, PulsarAdminException {
        Injector injector = Guice.createInjector(new SockkeeperModule(configuration, environment));
        BasicHealth basicHealth = injector.getInstance(BasicHealth.class);
        environment.healthChecks().register("basic", basicHealth);
        RedisHealth redisHealth = injector.getInstance(RedisHealth.class);
        environment.healthChecks().register("redis", redisHealth);
//        environment.jersey().register(injector.getInstance(PublishResource.class));
        environment.jersey().register(injector.getInstance(PublishResourceV4.class));
        String hostname = injector.getInstance(Key.get(String.class, Names.named("hostname")));

        ServletContextHandler contextHandler = environment.getApplicationContext();
        CuratorFramework curatorFramework = injector.getInstance(CuratorFramework.class);
        JakartaWebSocketServletContainerInitializer.configure(contextHandler, (servletContext, wsContainer) -> {
            ServerEndpointConfig.Configurator configurator =
                    new WebSocketConfigurator(curatorFramework, configuration, hostname,
                            injector.getInstance(RegisterResourceV4.class));
            ServerEndpointConfig config = ServerEndpointConfig.Builder
                    .create(RegisterResourceV4.class, "/v4/register/{userId}")
                    .configurator(configurator)
                    .build();
            wsContainer.addEndpoint(config);
        });

        PulsarClient pulsarClient = injector.getInstance(PulsarClient.class);
        JedisPool jedisPool = injector.getInstance(JedisPool.class);
        String sidelineTopic = injector.getInstance(Key.get(String.class, Names.named("sidelineTopic")));

        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);
        startSidelineConsumer(pulsarClient, jedisPool, sidelineTopic, hostname, basicHealth, objectMapper, configuration);
        startFailoverConsumers(hostname, sidelineTopic, pulsarClient, basicHealth, objectMapper, configuration.getAllTopicNames());
        startLivenessJob(jedisPool, hostname, basicHealth, configuration);

    }

    private static void startSidelineConsumer(PulsarClient pulsarClient,
                                              JedisPool jedisPool,
                                              String sidelineTopic,
                                              String hostname,
                                              BasicHealth basicHealth,
                                              ObjectMapper objectMapper,
                                              SockkeeperConfiguration configuration) throws PulsarClientException {
        MessageListener sidelineConsumer = new SidelineConsumer(
                pulsarClient,
                jedisPool,
                objectMapper, configuration);
        pulsarClient.newConsumer()
                .topic(sidelineTopic)
                .consumerName(Utils.getSidelineConsumerName(hostname))
                .subscriptionName(Utils.getSubscriptionName())
                .subscriptionType(SubscriptionType.Shared)
                .messageListener(sidelineConsumer)
                .enableRetry(true)
                .subscribe();
        basicHealth.markSidelineConsumerStarted();
    }

    private static void startFailoverConsumers(String hostname,
                                               String sidelineTopic,
                                               PulsarClient pulsarClient,
                                               BasicHealth basicHealth,
                                               ObjectMapper objectMapper,
                                               List<String> allTopics) throws PulsarClientException {
        String topicAssigned = Utils.getTopicNameForHost(hostname);
        allTopics.remove(topicAssigned);
        int i = 1;
        for (String topic : allTopics) {
            log.info("creating failover consumer on {}", topic);
            FailoverConsumer failoverConsumer = new FailoverConsumer(sidelineTopic, pulsarClient, objectMapper);
            pulsarClient.newConsumer()
                    .topic(topic)
                    .consumerName(Utils.getFailoverConsumerName(hostname))
                    .subscriptionName(Utils.getSubscriptionName())
                    .subscriptionType(SubscriptionType.Failover)
                    .messageListener(failoverConsumer)
                    .priorityLevel(i++)
                    .subscribe();
        }
        basicHealth.markFailoverConsumerStarted();
    }

    private void startLivenessJob(JedisPool jedisPool,
                                  String hostname,
                                  BasicHealth basicHealth,
                                  SockkeeperConfiguration configuration) {
        executorService.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(Utils.getKeyForHostLiveness(hostname),
                        configuration.getHostLivenessTTLInSeconds(),
                        hostname);
                basicHealth.markLivenessJobStarted(true);
            } catch (Exception e) {
                log.error("error occurred in liveness job", e);
                basicHealth.markLivenessJobStarted(false);
            }
        },
                configuration.getHostLivenessInitialDelayInSeconds(),
                configuration.getHostLivenessPeriodInSeconds(),
                TimeUnit.SECONDS);
    }

}
