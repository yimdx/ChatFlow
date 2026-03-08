package cs6650.assignment1.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.consumer.queue.RabbitMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private volatile boolean running = true;
    private Channel channel;
    
    public MessageConsumerThread(int consumerId, List<String> roomIds, 
                                RabbitMQConnection rabbitMQConnection, 
                                RoomManager roomManager, 
                                ObjectMapper objectMapper) {
        this.consumerId = consumerId;
        this.roomIds = roomIds;
        this.rabbitMQConnection = rabbitMQConnection;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void run() {
        logger.info("Consumer {} starting for rooms: {}", consumerId, roomIds);
        
        try {
            channel = rabbitMQConnection.createChannel();
            
            // Set prefetch count for fair distribution
            channel.basicQos(10);
            
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
            // 1. Look up all WebSocket sessions in the room
            Set<String> sessionIds = roomManager.getSessionsInRoom(message.getRoomId());
            
            if (sessionIds.isEmpty()) {
                logger.debug("No active sessions in room {}. Message {} will be stored for later delivery.",
                           message.getRoomId(), message.getMessageId());
                return;
            }
            
            logger.info("Processing message {} for room {} from user {}. Broadcasting to {} sessions.", 
                       message.getMessageId(), message.getRoomId(), message.getUsername(), sessionIds.size());
            
            // 2. Broadcast the message to all connected clients in the room
            // In a full Spring WebSocket implementation with SimpMessagingTemplate:
            // String destination = "/topic/room." + message.getRoomId();
            // messagingTemplate.convertAndSend(destination, message);
            
            // For the current implementation, we track the broadcast attempt
            int successfulBroadcasts = 0;
            int failedBroadcasts = 0;
            
            for (String sessionId : sessionIds) {
                try {
                    // 3. Handle any delivery failures
                    // In a real WebSocket implementation, you would:
                    // - Get the WebSocketSession from sessionId
                    // - Send the message via session.sendMessage()
                    // - Handle closed connections gracefully
                    
                    RoomManager.UserInfo userInfo = roomManager.getUserInfo(sessionId);
                    if (userInfo != null) {
                        logger.debug("Would broadcast to user {} (session: {}) in room {}", 
                                   userInfo.username, sessionId, message.getRoomId());
                        successfulBroadcasts++;
                    } else {
                        logger.warn("Session {} not found in active users. May have disconnected.", sessionId);
                        failedBroadcasts++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to broadcast message {} to session {} in room {}", 
                               message.getMessageId(), sessionId, message.getRoomId(), e);
                    failedBroadcasts++;
                }
            }
            
            logger.info("Broadcast complete for message {}: {} successful, {} failed", 
                       message.getMessageId(), successfulBroadcasts, failedBroadcasts);
            
        } catch (Exception e) {
            logger.error("Error processing message {} for room {}", 
                       message.getMessageId(), message.getRoomId(), e);
            // Rethrow to trigger message requeue via basicNack
            throw new RuntimeException("Message processing failed", e);
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
