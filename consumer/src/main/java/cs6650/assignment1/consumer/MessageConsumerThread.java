package cs6650.assignment1.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.consumer.queue.RabbitMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * MessageConsumerThread consumes messages from a specific set of room queues.
 * Each consumer thread handles multiple rooms for load distribution.
 */
public class MessageConsumerThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerThread.class);
    
    private final int consumerId;
    private final List<String> roomIds;
    private final RabbitMQConnection rabbitMQConnection;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final List<String> serverUrls;
    private final HttpClient httpClient;
    private volatile boolean running = true;
    private Channel channel;
    
    public MessageConsumerThread(int consumerId, List<String> roomIds, 
                                RabbitMQConnection rabbitMQConnection, 
                                RoomManager roomManager, 
                                ObjectMapper objectMapper,
                                List<String> serverUrls) {
        this.consumerId = consumerId;
        this.roomIds = roomIds;
        this.rabbitMQConnection = rabbitMQConnection;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.serverUrls = serverUrls;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }
    
    @Override
    public void run() {
        logger.info("Consumer {} starting for rooms: {}", consumerId, roomIds);
        
        try {
            channel = rabbitMQConnection.createChannel();
            
            // Set prefetch count for fair distribution
            // Increased from 10 to 50 for better throughput
            channel.basicQos(50);
            
            // Start consuming from each assigned room queue
            for (String roomId : roomIds) {
                String queueName = "room." + roomId;
                
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    try {
                        String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        logger.debug("Consumer {} received message from {}: {}", 
                                   consumerId, queueName, messageJson);
                        
                        // Parse queue message
                        QueueMessage queueMessage = objectMapper.readValue(messageJson, QueueMessage.class);
                        
                        // Process message (in a real implementation, broadcast to WebSocket clients)
                        processMessage(queueMessage);
                        
                        // Acknowledge message
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        
                        roomManager.incrementMessagesProcessed();
                        
                    } catch (Exception e) {
                        logger.error("Error processing message from {}: {}", queueName, e.getMessage(), e);
                        
                        // Negative acknowledge with requeue
                        try {
                            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                        } catch (IOException ioException) {
                            logger.error("Error sending NACK", ioException);
                        }
                    }
                };
                
                // Start consuming with manual acknowledgment
                channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
                
                logger.info("Consumer {} started consuming from queue: {}", consumerId, queueName);
            }
            
            // Keep thread alive
            while (running) {
                Thread.sleep(1000);
            }
            
        } catch (IOException | InterruptedException e) {
            logger.error("Consumer {} encountered error: {}", consumerId, e.getMessage(), e);
        } finally {
            cleanup();
        }
        
        logger.info("Consumer {} stopped", consumerId);
    }
    
    private void processMessage(QueueMessage message) {
        try {
            // Changed from info to debug - logging every message kills performance
            logger.debug("Processing message {} for room {} from user {}.", 
                       message.getMessageId(), message.getRoomId(), message.getUsername());
            
            // Broadcast to all servers via HTTP POST
            broadcastToServers(message);
            
        } catch (Exception e) {
            logger.error("Error processing message {} for room {}", 
                       message.getMessageId(), message.getRoomId(), e);
            // Rethrow to trigger message requeue via basicNack
            throw new RuntimeException("Message processing failed", e);
        }
    }
    
    private void broadcastToServers(QueueMessage message) {
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            logger.error("Error serializing message", e);
            return;
        }
        
        // PARALLEL broadcast to all servers (non-blocking, using common ForkJoinPool)
        List<java.util.concurrent.CompletableFuture<Integer>> futures = new java.util.ArrayList<>();
        
        for (String serverUrl : serverUrls) {
            java.util.concurrent.CompletableFuture<Integer> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/broadcast"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(messageJson))
                        .timeout(Duration.ofSeconds(2)) // Reduced from 3s to 2s
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        logger.debug("Successfully broadcast to server {}", serverUrl);
                        return 1; // success
                    } else {
                        logger.warn("Server {} returned status {}", serverUrl, response.statusCode());
                        return 0; // fail
                    }
                        
                } catch (Exception e) {
                    logger.error("Error broadcasting to server {}: {}", serverUrl, e.getMessage());
                    return 0; // fail
                }
            });
            futures.add(future);
        }
        
        // Wait for all broadcasts to complete (parallel execution)
        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .get(3, java.util.concurrent.TimeUnit.SECONDS); // Overall timeout
            
            // Only log failures, not every successful broadcast
            int failCount = 0;
            for (java.util.concurrent.CompletableFuture<Integer> f : futures) {
                try {
                    if (f.get() == 0) failCount++;
                } catch (Exception e) {
                    failCount++;
                }
            }
            
            if (failCount > 0) {
                logger.warn("Broadcast for message {} had {} failures out of {}", 
                           message.getMessageId(), failCount, futures.size());
            }
        } catch (Exception e) {
            logger.error("Timeout waiting for broadcasts to complete", e);
        }
    }
    
    public void stop() {
        running = false;
    }
    
    private void cleanup() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (Exception e) {
                logger.error("Error closing channel for consumer {}", consumerId, e);
            }
        }
    }
}
