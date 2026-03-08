package cs6650.assignment1.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.ChatResponse;
import cs6650.assignment1.model.ErrorResponse;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.queue.MessagePublisher;
import cs6650.assignment1.validation.MessageValidator;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatWebSocketServer extends WebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketServer.class);
    private final ObjectMapper objectMapper;
    private final Map<WebSocket, String> connectionRooms;
    private final Map<String, Set<WebSocket>> roomConnections; // Track connections per room
    private final Pattern roomPattern = Pattern.compile("^/chat/(\\d+)$");
    private final MessagePublisher messagePublisher;
    private final String serverId;
    
    public ChatWebSocketServer(int port, MessagePublisher messagePublisher, String serverId) {
        super(new InetSocketAddress(port));
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.connectionRooms = new ConcurrentHashMap<>();
        this.roomConnections = new ConcurrentHashMap<>();
        this.messagePublisher = messagePublisher;
        this.serverId = serverId;
        
        logger.info("ChatWebSocketServer initialized on port {} with serverId: {}", port, serverId);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String resourceDescriptor = handshake.getResourceDescriptor();
        logger.debug("New connection attempt from {}: {}", conn.getRemoteSocketAddress(), resourceDescriptor);
        
        // Validate and extract room ID from path
        Matcher matcher = roomPattern.matcher(resourceDescriptor);
        if (matcher.matches()) {
            String roomId = matcher.group(1);
            int roomNum = Integer.parseInt(roomId);
            
            // Validate room number is between 1-20
            if (roomNum >= 1 && roomNum <= 20) {
                connectionRooms.put(conn, roomId);
                roomConnections.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(conn);
                logger.info("Client connected to room {}. Room now has {} connections.",
                           roomId, roomConnections.get(roomId).size());
            } else {
                logger.warn("Invalid room number: {} (must be 1-20)", roomNum);
                conn.close(1003, "Invalid room number. Room must be between 1 and 20");
            }
        } else {
            logger.warn("Invalid connection path: {}", resourceDescriptor);
            conn.close(1003, "Invalid endpoint. Use /chat/{roomId} where roomId is 1-20");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = connectionRooms.remove(conn);
        if (roomId != null) {
            Set<WebSocket> connections = roomConnections.get(roomId);
            if (connections != null) {
                connections.remove(conn);
                if (connections.isEmpty()) {
                    roomConnections.remove(roomId);
                }
            }
        }
        logger.info("Connection closed for room {}: {} - {}", roomId, code, reason);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        String roomId = connectionRooms.get(conn);
        logger.debug("Message received in room {}: {}", roomId, message);
        
        try {
            // Parse incoming message
            ChatMessage chatMessage = objectMapper.readValue(message, ChatMessage.class);
            
            // Validate message
            List<String> validationErrors = MessageValidator.validate(chatMessage);
            
            if (!validationErrors.isEmpty()) {
                // Send error response
                ErrorResponse errorResponse = new ErrorResponse(validationErrors);
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                conn.send(errorJson);
                return;
            }
            
            // Get client IP
            String clientIp = conn.getRemoteSocketAddress().getAddress().getHostAddress();
            
            // Create queue message and publish to RabbitMQ
            QueueMessage queueMessage = QueueMessage.fromChatMessage(
                chatMessage, 
                roomId, 
                serverId, 
                clientIp
            );
            
            // Publish to RabbitMQ
            messagePublisher.publishMessage(queueMessage);
            
            // Send success acknowledgment back to sender
            ChatResponse response = new ChatResponse(
                chatMessage.getUserId(),
                chatMessage.getUsername(),
                chatMessage.getMessage(),
                chatMessage.getTimestamp(),
                Instant.now(),
                chatMessage.getMessageType(),
                "success"
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            conn.send(responseJson);
            
            logger.info("Published message {} to room {} from user {}", 
                       queueMessage.getMessageId(), roomId, chatMessage.getUsername());
            
        } catch (Exception e) {
            logger.error("Error processing message in room {}: {}", roomId, e.getMessage(), e);
            
            try {
                ErrorResponse errorResponse = new ErrorResponse(
                    List.of("Failed to process message: " + e.getMessage())
                );
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                conn.send(errorJson);
            } catch (Exception ex) {
                logger.error("Error sending error response", ex);
            }
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        String roomId = conn != null ? connectionRooms.get(conn) : "unknown";
        logger.error("WebSocket error for room {}: {}", roomId, ex.getMessage(), ex);
    }
    
    @Override
    public void onStart() {
        logger.info("ChatWebSocketServer started successfully!");
        logger.info("Listening on port {}", this.getPort());
        logger.info("WebSocket endpoint: ws://localhost:{}/chat/{{roomId}}", this.getPort());
        setConnectionLostTimeout(100);
    }
    
    public int getActiveConnections() {
        return connectionRooms.size();
    }
    
    /**
     * Broadcast a message to all clients connected to a specific room.
     * This method is called by the BroadcastConsumer when it receives messages
     * from the consumer via RabbitMQ.
     */
    public void broadcastToRoom(QueueMessage queueMessage) {
        String roomId = queueMessage.getRoomId();
        Set<WebSocket> connections = roomConnections.get(roomId);
        
        if (connections == null || connections.isEmpty()) {
            logger.debug("No connections in room {} to broadcast to", roomId);
            return;
        }
        
        try {
            // Convert QueueMessage to ChatResponse for clients
            ChatResponse response = new ChatResponse(
                Integer.parseInt(queueMessage.getUserId()),
                queueMessage.getUsername(),
                queueMessage.getMessage(),
                queueMessage.getTimestamp(),
                Instant.now(), // Server broadcast timestamp
                queueMessage.getMessageType(),
                "broadcast"
            );
            
            String responseJson = objectMapper.writeValueAsString(response);
            
            int successCount = 0;
            int failCount = 0;
            
            // Broadcast to all connections in the room
            for (WebSocket conn : connections) {
                try {
                    if (conn.isOpen()) {
                        conn.send(responseJson);
                        successCount++;
                    } else {
                        logger.warn("Connection in room {} is not open, skipping", roomId);
                        failCount++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to send message to connection in room {}", roomId, e);
                    failCount++;
                }
            }
            
            logger.info("Broadcasted message {} to room {}: {} successful, {} failed", 
                       queueMessage.getMessageId(), roomId, successCount, failCount);
            
        } catch (Exception e) {
            logger.error("Error broadcasting message {} to room {}", 
                        queueMessage.getMessageId(), roomId, e);
        }
    }
}
