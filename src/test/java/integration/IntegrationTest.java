package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.redis.testcontainers.RedisContainer;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.curator.test.TestingServer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sockkeeper.SockkeeperApplication;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

public class IntegrationTest {

    private static DropwizardAppExtension<SockkeeperConfiguration> sockkeeperApp;
    private static TestingServer zkServer;
    private static PulsarContainer pulsarContainer;
    private static RedisContainer redis;


    @BeforeAll
    public static void setup() throws Exception {
        zkServer = new TestingServer(2181, true);

        pulsarContainer = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.0.2"));
        pulsarContainer.setPortBindings(Arrays.asList("6650:6650", "8080:8080"));
        pulsarContainer.start();

        redis = new RedisContainer(DockerImageName.parse("redis:7.2.5"));
        redis.setPortBindings(List.of("6379:6379"));
        redis.start();

        sockkeeperApp = new DropwizardAppExtension<>(
                SockkeeperApplication.class,
                ResourceHelpers.resourceFilePath("test-config.yml")
        );
        sockkeeperApp.before();
    }

    @Test
    public void testMessageDelivery() throws Exception {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

        CompletableFuture<WebSocket> webSocketCompletableFuture = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                        URI.create(String.format("ws://localhost:%d/v4/register/santanu",
                                sockkeeperApp.getLocalPort())
                        ), new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket webSocket) {
                                WebSocket.Listener.super.onOpen(webSocket);
                            }

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data,
                                                             boolean last) {
                                String text = new String(String.valueOf(data));
                                queue.offer(text);
                                return WebSocket.Listener.super.onText(webSocket, data, last);
                            }
                        });
        WebSocket webSocket = webSocketCompletableFuture.get();

        Client client = sockkeeperApp.client();

        waitForHealthCheckSuccess(client, Duration.ofSeconds(10));

        String message = "hello world";
        try (Response response = client.target(
                        String.format("http://localhost:%d/v4/publish/santanu", sockkeeperApp.getLocalPort()))
                .request()
                .post(Entity.text(message))) {

            assertTrue(HttpStatus.isSuccess(response.getStatus()));
        }

        String polledMessage = queue.poll(30, TimeUnit.SECONDS);
        assertEquals(message, polledMessage);

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        webSocket.abort();
    }

    private static void waitForHealthCheckSuccess(Client client, Duration timeout) throws Exception {
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
    public static void tearDown() throws IOException, InterruptedException {
        sockkeeperApp.after();
        zkServer.stop();
        pulsarContainer.stop();
        redis.stop();
    }

}
