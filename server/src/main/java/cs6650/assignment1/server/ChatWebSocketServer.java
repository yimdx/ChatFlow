package cs6650.assignment1.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.ChatResponse;
import cs6650.assignment1.model.ErrorResponse;
import cs6650.assignment1.validation.MessageValidator;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatWebSocketServer extends WebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketServer.class);
    private final ObjectMapper objectMapper;
    private final Map<WebSocket, String> connectionRooms;
    private final Pattern roomPattern = Pattern.compile("^/chat/(\\d+)$");  // Strict: only /chat/{roomId}
    
    public ChatWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.connectionRooms = new ConcurrentHashMap<>();
        
        logger.info("ChatWebSocketServer initialized on port {}", port);
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
                logger.info("Client connected to room {}", roomId);
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
                // logger.warn("Validation failed for room {}: {}", roomId, validationErrors);
                return;
            }
            
            // Create success response
            ChatResponse response = new ChatResponse(
                chatMessage.getUserId(),
                chatMessage.getUsername(),
                chatMessage.getMessage(),
                chatMessage.getTimestamp(),
                Instant.now(),
                chatMessage.getMessageType(),
                "success"
            );
            
            // Send response back to client
            String responseJson = objectMapper.writeValueAsString(response);
            conn.send(responseJson);
            
            logger.debug("Processed message in room {} from user {}", 
                        roomId, chatMessage.getUsername());
            
        } catch (Exception e) {
            logger.error("Error processing message in room {}: {}", roomId, e.getMessage(), e);
            
            try {
                ErrorResponse errorResponse = new ErrorResponse(
                    List.of("Invalid message format: " + e.getMessage())
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
}
