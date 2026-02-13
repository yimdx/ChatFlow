package cs6650.assignment1.client;

import cs6650.assignment1.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageSender implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private static final int MAX_RETRIES = 5;
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    
    private final BlockingQueue<ChatMessage> messageQueue;
    private final String serverUrl;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final AtomicInteger reconnectionCount;
    private final int messagesToSend;
    private final Random random;
    
    public MessageSender(BlockingQueue<ChatMessage> messageQueue, String serverUrl, 
                        AtomicInteger successCount, AtomicInteger failureCount, 
                        AtomicInteger reconnectionCount, int messagesToSend) {
        this.messageQueue = messageQueue;
        this.serverUrl = serverUrl;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.reconnectionCount = reconnectionCount;
        this.messagesToSend = messagesToSend;
        this.random = new Random();
    }
    
    @Override
    public void run() {
        ChatWebSocketClient client = null;
        
        try {
            // Establish WebSocket connection
            int roomId = random.nextInt(20) + 1;
            URI serverUri = new URI(serverUrl + "/chat/" + roomId);
            client = new ChatWebSocketClient(serverUri, successCount, failureCount);
            
            boolean connected = client.connectBlocking();
            if (!connected) {
                logger.error("Failed to connect to server");
                return;
            }
            
            // Send messages
            for (int i = 0; i < messagesToSend; i++) {
                ChatMessage message = messageQueue.take();
                
                boolean sent = client.sendChatMessage(message, MAX_RETRIES);
                if (!sent) {
                    failureCount.incrementAndGet();
                    logger.warn("Failed to send message after {} retries", MAX_RETRIES);
                }
                
                // Small delay to prevent overwhelming the server
                if (i % 100 == 0) {
                    Thread.sleep(1);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in message sender", e);
        } finally {
            if (client != null) {
                try {
                    client.closeBlocking();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
