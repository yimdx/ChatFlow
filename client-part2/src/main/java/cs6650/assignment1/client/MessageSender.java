package cs6650.assignment1.client;

import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.MetricRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageSender implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private static final int MAX_RETRIES = 5;
    
    private final BlockingQueue<ChatMessage> messageQueue;
    private final String serverUrl;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final AtomicInteger reconnectionCount;
    private final int messagesToSend;
    private final Random random;
    private final BlockingQueue<MetricRecord> metricsQueue;
    private final int roomId;
    
    public MessageSender(BlockingQueue<ChatMessage> messageQueue, String serverUrl, 
                        AtomicInteger successCount, AtomicInteger failureCount, 
                        AtomicInteger reconnectionCount, int messagesToSend,
                        BlockingQueue<MetricRecord> metricsQueue) {
        this.messageQueue = messageQueue;
        this.serverUrl = serverUrl;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.reconnectionCount = reconnectionCount;
        this.messagesToSend = messagesToSend;
        this.random = new Random();
        this.metricsQueue = metricsQueue;
        this.roomId = random.nextInt(20) + 1;
    }
    
    @Override
    public void run() {
        ChatWebSocketClient client = null;
        
        try {
            // Establish WebSocket connection
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
                
                boolean sent = false;
                for (int attempt = 0; attempt < MAX_RETRIES && !sent; attempt++) {
                    try {
                        long sendTimestamp = System.currentTimeMillis();
                        CompletableFuture<ChatWebSocketClient.ResponseData> future = 
                            client.sendChatMessageAsync(message);
                        
                        ChatWebSocketClient.ResponseData response = future.get();
                        
                        // Record metrics
                        MetricRecord metric = new MetricRecord(
                            sendTimestamp,
                            message.getMessageType().toString(),
                            response.latencyMs,
                            response.status,
                            roomId
                        );
                        metricsQueue.offer(metric);
                        
                        sent = true;
                    } catch (Exception e) {
                        logger.warn("Send attempt {} failed", attempt + 1);
                        if (attempt < MAX_RETRIES - 1) {
                            Thread.sleep((long) Math.pow(2, attempt) * 100);
                        }
                    }
                }
                
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
