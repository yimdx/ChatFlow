package cs6650.assignment1.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.ChatResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatWebSocketClient extends WebSocketClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketClient.class);
    private final ObjectMapper objectMapper;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private CountDownLatch responseLatch;
    
    public ChatWebSocketClient(URI serverUri, AtomicInteger successCount, AtomicInteger failureCount) {
        super(serverUri);
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.debug("WebSocket connection opened");
    }
    
    @Override
    public void onMessage(String message) {
        try {
            ChatResponse response = objectMapper.readValue(message, ChatResponse.class);
            if ("success".equalsIgnoreCase(response.getStatus())) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        } catch (Exception e) {
            logger.error("Error parsing response: {}", message, e);
            failureCount.incrementAndGet();
        } finally {
            if (responseLatch != null) {
                responseLatch.countDown();
            }
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.debug("WebSocket connection closed: {} - {}", code, reason);
    }
    
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error", ex);
    }
    
    public boolean sendChatMessage(ChatMessage chatMessage, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                responseLatch = new CountDownLatch(1);
                String json = objectMapper.writeValueAsString(chatMessage);
                send(json);
                return true;
            } catch (Exception e) {
                logger.error("Error sending message (attempt {}/{})", attempt + 1, maxRetries, e);
                if (attempt < maxRetries - 1) {
                    try {
                        // Exponential backoff
                        Thread.sleep((long) Math.pow(2, attempt) * 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }
}
