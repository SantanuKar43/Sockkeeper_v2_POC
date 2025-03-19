package org.sockkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SidelineMessageDeliveryIT extends BaseIT {

    @BeforeEach
    public void setup() throws Exception {
        sockkeeperApp = new DropwizardAppExtension<>(
                SockkeeperApplication.class,
                ResourceHelpers.resourceFilePath("test-config.yml")
        );
        sockkeeperApp.before();
        client = sockkeeperApp.client();

        waitForHealthCheckSuccess(client, Duration.ofSeconds(10));
    }

    @Test
    public void validateSidelineConsumer() throws Exception {
        // publish
        String message = UUID.randomUUID().toString();
        try (Response response = client.target(
                        String.format("http://localhost:%d/v4/publish/santanu", sockkeeperApp.getLocalPort()))
                .request()
                .post(Entity.text(message))) {

            assertTrue(HttpStatus.isSuccess(response.getStatus()));
        }


        //connect and listen for message
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
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            webSocket.sendPing(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8)));
        }, 5, 20, TimeUnit.SECONDS);

        //wait for message
        String polledMessage = queue.poll(30, TimeUnit.SECONDS);

        //close socket
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        webSocket.abort();

        //verify
        assertEquals(message, polledMessage);
    }

    @AfterEach
    public void tearDownAll() {
        sockkeeperApp.after();
    }
}
