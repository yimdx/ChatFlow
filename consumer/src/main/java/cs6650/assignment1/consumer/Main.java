package cs6650.assignment1.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cs6650.assignment1.consumer.queue.RabbitMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Configuration - can be overridden by environment variables
    private static final String RABBITMQ_HOST = getEnv("RABBITMQ_HOST", "localhost");
    private static final int RABBITMQ_PORT = getEnvInt("RABBITMQ_PORT", 5672);
    private static final String RABBITMQ_USERNAME = getEnv("RABBITMQ_USERNAME", "guest");
    private static final String RABBITMQ_PASSWORD = getEnv("RABBITMQ_PASSWORD", "guest");
    private static final int CONSUMER_THREAD_COUNT = getEnvInt("CONSUMER_THREAD_COUNT", 20);
    private static final int ROOM_COUNT = getEnvInt("ROOM_COUNT", 20);
    
    // Server URLs for HTTP broadcasting (comma-separated)
    private static final String SERVER_URLS = getEnv("SERVER_URLS", "http://localhost:8082");
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("ChatFlow Message Consumer");
        logger.info("========================================");
        logger.info("RabbitMQ: {}:{}", RABBITMQ_HOST, RABBITMQ_PORT);
        logger.info("Consumer threads: {}", CONSUMER_THREAD_COUNT);
        logger.info("Room count: {}", ROOM_COUNT);
        logger.info("Broadcasting to servers: {}", SERVER_URLS);
        logger.info("========================================");
        
        RabbitMQConnection rabbitMQConnection = null;
        ExecutorService executorService = null;
        List<MessageConsumerThread> consumers = new ArrayList<>();
        
        try {
            // Connect to RabbitMQ
            rabbitMQConnection = new RabbitMQConnection(
                RABBITMQ_HOST, 
                RABBITMQ_PORT, 
                RABBITMQ_USERNAME, 
                RABBITMQ_PASSWORD
            );
            rabbitMQConnection.connect();
            
            // Create ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            
            // Parse server URLs
            List<String> serverUrls = Arrays.asList(SERVER_URLS.split(","));
            for (int i = 0; i < serverUrls.size(); i++) {
                serverUrls.set(i, serverUrls.get(i).trim());
            }
            
            // Create room manager
            RoomManager roomManager = new RoomManager();
            
            // Create executor service for consumer threads
            executorService = Executors.newFixedThreadPool(CONSUMER_THREAD_COUNT);
            
            // Distribute rooms across consumer threads
            List<List<String>> roomDistribution = distributeRooms(ROOM_COUNT, CONSUMER_THREAD_COUNT);
            
            // Create and start consumer threads
            for (int i = 0; i < CONSUMER_THREAD_COUNT; i++) {
                List<String> assignedRooms = roomDistribution.get(i);
                if (!assignedRooms.isEmpty()) {
                    MessageConsumerThread consumer = new MessageConsumerThread(
                        i + 1,
                        assignedRooms,
                        rabbitMQConnection,
                        roomManager,
                        objectMapper,
                        serverUrls
                    );
                    consumers.add(consumer);
                    executorService.submit(consumer);
                }
            }
            
            logger.info("========================================");
            logger.info("Consumer application started!");
            logger.info("{} consumer threads running", consumers.size());
            logger.info("Press Ctrl+C to stop");
            logger.info("========================================");
            
            // Setup shutdown hook
            final RabbitMQConnection finalConnection = rabbitMQConnection;
            final ExecutorService finalExecutor = executorService;
            final List<MessageConsumerThread> finalConsumers = consumers;
            final RoomManager finalRoomManager = roomManager;
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down consumer application...");
                
                // Stop all consumer threads
                for (MessageConsumerThread consumer : finalConsumers) {
                    consumer.stop();
                }
                
                // Shutdown executor
                if (finalExecutor != null) {
                    finalExecutor.shutdown();
                    try {
                        if (!finalExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                            finalExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        finalExecutor.shutdownNow();
                    }
                }
                
                // Close RabbitMQ connection
                if (finalConnection != null) {
                    finalConnection.close();
                }
                
                logger.info("Total messages processed: {}", finalRoomManager.getMessagesProcessed());
                logger.info("Consumer application stopped");
            }));
            
            // Start statistics reporter
            startStatisticsReporter(roomManager);
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Fatal error in consumer application", e);
            
            // Cleanup on failure
            if (rabbitMQConnection != null) {
                rabbitMQConnection.close();
            }
            if (executorService != null) {
                executorService.shutdownNow();
            }
            
            System.exit(1);
        }
    }
    
    /**
     * Distribute rooms evenly across consumer threads
     */
    private static List<List<String>> distributeRooms(int roomCount, int consumerCount) {
        List<List<String>> distribution = new ArrayList<>();
        
        for (int i = 0; i < consumerCount; i++) {
            distribution.add(new ArrayList<>());
        }
        
        for (int roomId = 1; roomId <= roomCount; roomId++) {
            int consumerIndex = (roomId - 1) % consumerCount;
            distribution.get(consumerIndex).add(String.valueOf(roomId));
        }
        
        logger.info("Room distribution:");
        for (int i = 0; i < distribution.size(); i++) {
            if (!distribution.get(i).isEmpty()) {
                logger.info("  Consumer {}: rooms {}", i + 1, distribution.get(i));
            }
        }
        
        return distribution;
    }
    
    /**
     * Start a thread that periodically reports statistics
     */
    private static void startStatisticsReporter(RoomManager roomManager) {
        Thread statsThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(30000); // Report every 30 seconds
                    
                    logger.info("=== Statistics ===");
                    logger.info("Messages processed: {}", roomManager.getMessagesProcessed());
                    logger.info("Active rooms: {}", roomManager.getActiveRoomCount());
                    logger.info("Active users: {}", roomManager.getTotalActiveUsers());
                    logger.info("==================");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        statsThread.setDaemon(true);
        statsThread.setName("statistics-reporter");
        statsThread.start();
    }
    
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
    
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
