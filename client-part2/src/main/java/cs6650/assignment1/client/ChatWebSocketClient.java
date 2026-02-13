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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatWebSocketClient extends WebSocketClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketClient.class);
    private final ObjectMapper objectMapper;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final ConcurrentHashMap<String, MessageContext> pendingMessages;
    
    private static class MessageContext {
        final long sendTimestamp;
        final CompletableFuture<ResponseData> future;
        
        MessageContext(long sendTimestamp, CompletableFuture<ResponseData> future) {
            this.sendTimestamp = sendTimestamp;
            this.future = future;
        }
    }
    
    public static class ResponseData {
        public final String status;
        public final long latencyMs;
        
        public ResponseData(String status, long latencyMs) {
            this.status = status;
            this.latencyMs = latencyMs;
        }
    }
    
    public ChatWebSocketClient(URI serverUri, AtomicInteger successCount, AtomicInteger failureCount) {
        super(serverUri);
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.pendingMessages = new ConcurrentHashMap<>();
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.debug("WebSocket connection opened");
    }
    
    @Override
    public void onMessage(String message) {
        try {
            ChatResponse response = objectMapper.readValue(message, ChatResponse.class);
            long receiveTimestamp = System.currentTimeMillis();
            
            // Find the corresponding pending message (use a simple approach)
            if (!pendingMessages.isEmpty()) {
                // Get the first pending message (FIFO approximation)
                String firstKey = pendingMessages.keys().nextElement();
                MessageContext ctx = pendingMessages.remove(firstKey);
                
                if (ctx != null) {
                    long latencyMs = receiveTimestamp - ctx.sendTimestamp;
                    String status = response.getStatus() != null ? response.getStatus() : "unknown";
                    
                    if ("success".equalsIgnoreCase(status)) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                    
                    ctx.future.complete(new ResponseData(status, latencyMs));
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing response: {}", message, e);
            failureCount.incrementAndGet();
            
            // Complete any pending futures with error
            if (!pendingMessages.isEmpty()) {
                String firstKey = pendingMessages.keys().nextElement();
                MessageContext ctx = pendingMessages.remove(firstKey);
                if (ctx != null) {
                    ctx.future.complete(new ResponseData("error", 0));
                }
            }
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.debug("WebSocket connection closed: {} - {}", code, reason);
        // Complete all pending futures with failure
        pendingMessages.values().forEach(ctx -> 
            ctx.future.complete(new ResponseData("connection_closed", 0))
        );
        pendingMessages.clear();
    }
    
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error", ex);
    }
    
    public CompletableFuture<ResponseData> sendChatMessageAsync(ChatMessage chatMessage) {
        CompletableFuture<ResponseData> future = new CompletableFuture<>();
        
        try {
            long sendTimestamp = System.currentTimeMillis();
            String json = objectMapper.writeValueAsString(chatMessage);
            String messageId = String.valueOf(sendTimestamp);
            
            pendingMessages.put(messageId, new MessageContext(sendTimestamp, future));
            send(json);
            
            // Set a timeout
            future.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    pendingMessages.remove(messageId);
                    return new ResponseData("timeout", 0);
                });
        } catch (Exception e) {
            logger.error("Error sending message", e);
            future.complete(new ResponseData("error", 0));
        }
        
        return future;
    }
}
