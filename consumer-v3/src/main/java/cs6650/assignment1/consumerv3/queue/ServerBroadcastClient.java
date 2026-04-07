package cs6650.assignment1.consumerv3.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs6650.assignment1.consumerv3.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerBroadcastClient {
    private static final Logger logger = LoggerFactory.getLogger(ServerBroadcastClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI broadcastEndpoint;
    private final URI batchEndpoint;

    public ServerBroadcastClient(String broadcastBaseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        URI base = URI.create(normalizeBaseUrl(broadcastBaseUrl));
        this.broadcastEndpoint = base.resolve("/broadcast");
        this.batchEndpoint = base.resolve("/broadcast/batch");
    }

    public void broadcastBatch(List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(messages);
            HttpRequest request = HttpRequest.newBuilder(batchEndpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("Broadcasted {} message(s) to {}", messages.size(), batchEndpoint);
            } else {
                logger.warn("Broadcast batch to {} returned status {}", batchEndpoint, response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Broadcast batch interrupted for {} message(s)", messages.size(), e);
        } catch (IOException e) {
            logger.warn("Broadcast batch failed for {} message(s)", messages.size(), e);
        }
    }

    public void broadcastSingle(QueueMessage message) {
        if (message == null) {
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(message);
            HttpRequest request = HttpRequest.newBuilder(broadcastEndpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("Broadcasted single message {} to {}", message.getMessageId(), request.uri());
            } else {
                logger.warn("Broadcast single to {} returned status {}", request.uri(), response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Broadcast single interrupted for message {}", message.getMessageId(), e);
        } catch (IOException e) {
            logger.warn("Broadcast single failed for message {}", message.getMessageId(), e);
        }
    }

    public CompletableFuture<Void> broadcastSingleAsync(QueueMessage message) {
        if (message == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            String body = objectMapper.writeValueAsString(message);
            HttpRequest request = HttpRequest.newBuilder(broadcastEndpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.debug("Broadcasted single message {} to {}", message.getMessageId(), request.uri());
                    } else {
                        logger.warn("Broadcast single to {} returned status {}", request.uri(), response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    logger.warn("Broadcast single failed for message {}", message.getMessageId(), ex);
                    return null;
                });
        } catch (IOException e) {
            logger.warn("Broadcast single serialization failed for message {}", message.getMessageId(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String normalizeBaseUrl(String broadcastBaseUrl) {
        if (broadcastBaseUrl == null || broadcastBaseUrl.isBlank()) {
            return "http://localhost:8082";
        }
        return broadcastBaseUrl.endsWith("/") ? broadcastBaseUrl.substring(0, broadcastBaseUrl.length() - 1) : broadcastBaseUrl;
    }
}
