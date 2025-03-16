package org.sockkeeper;

import com.redis.testcontainers.RedisContainer;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.curator.test.TestingServer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

public class BaseIT {

    protected static DropwizardAppExtension<SockkeeperConfiguration> sockkeeperApp;
    protected static TestingServer zkServer;
    protected static PulsarContainer pulsarContainer;
    protected static RedisContainer redis;
    protected static Client client;

    static {
        try {
            zkServer = new TestingServer(2181, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        pulsarContainer = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.0.2"));
        pulsarContainer.setPortBindings(Arrays.asList("6650:6650", "8080:8080"));
        pulsarContainer.start();

        redis = new RedisContainer(DockerImageName.parse("redis:7.2.5"));
        redis.setPortBindings(List.of("6379:6379"));
        redis.start();
    }

    @BeforeAll
    public static void setupAll() throws Exception {
        sockkeeperApp = new DropwizardAppExtension<>(
                SockkeeperApplication.class,
                ResourceHelpers.resourceFilePath("test-config.yml")
        );
        sockkeeperApp.before();
        client = sockkeeperApp.client();
    }

    protected static void waitForHealthCheckSuccess(Client client, Duration timeout) throws Exception {
        AtomicBoolean healthy = new AtomicBoolean(false);
        Instant start = Instant.now();
        while (!healthy.get()) {
            if (Instant.now().isAfter(start.plus(timeout))) {
                throw new Exception("Health check timed out!");
            }
            try (Response response = client.target(
                            String.format("http://localhost:%d/healthcheck", sockkeeperApp.getAdminPort()))
                    .request()
                    .get()) {
                healthy.set(HttpStatus.isSuccess(response.getStatus()));
                Thread.sleep(1000);
            }
        }
    }

    @AfterAll
    public static void tearDownAll() throws IOException, InterruptedException {
        sockkeeperApp.after();
    }

}
