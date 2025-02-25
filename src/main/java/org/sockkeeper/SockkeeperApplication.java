package org.sockkeeper;

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
import org.apache.pulsar.client.admin.PulsarAdmin;
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
import org.sockkeeper.health.Basic;
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
        environment.healthChecks().register("basic", injector.getInstance(Basic.class));
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

        startSidelineConsumer(pulsarClient, jedisPool, sidelineTopic, hostname);
        startFailoverConsumers(hostname, injector, sidelineTopic, pulsarClient);
        startLivenessJob(jedisPool, hostname);

    }

    private static void startSidelineConsumer(PulsarClient pulsarClient, JedisPool jedisPool, String sidelineTopic, String hostname) throws PulsarClientException {
        MessageListener sidelineConsumer = new SidelineConsumer(pulsarClient, jedisPool);
        pulsarClient.newConsumer()
                .topic(sidelineTopic)
                .consumerName(Utils.getSidelineConsumerName(hostname))
                .subscriptionName(Utils.getSubscriptionName())
                .subscriptionType(SubscriptionType.Shared)
                .messageListener(sidelineConsumer)
                .subscribe();
    }

    private static void startFailoverConsumers(String hostname, Injector injector, String sidelineTopic, PulsarClient pulsarClient) throws PulsarAdminException, PulsarClientException {
        String topicAssigned = Utils.getTopicNameForHost(hostname);
        PulsarAdmin pulsarAdmin = injector.getInstance(PulsarAdmin.class);
        List<String> allTopics = pulsarAdmin.topics().getList("public/default");
        allTopics.remove(topicAssigned);
        int i = 1;
        for (String topic : allTopics) {
            log.info("creating failover consumer on {}", topic);
            if (!topic.contains("sk-node-") || topic.contains(topicAssigned)) {
                continue;
            }
            FailoverConsumer failoverConsumer = new FailoverConsumer(sidelineTopic, pulsarClient);
            pulsarClient.newConsumer()
                    .topic(topic)
                    .consumerName(Utils.getFailoverConsumerName(hostname))
                    .subscriptionName(Utils.getSubscriptionName())
                    .subscriptionType(SubscriptionType.Failover)
                    .messageListener(failoverConsumer)
                    .priorityLevel(i++)
                    .subscribe();
        }
    }

    private void startLivenessJob(JedisPool jedisPool, String hostname) {
        executorService.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(Utils.getKeyForHostLiveness(hostname), 15, hostname);
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

}
