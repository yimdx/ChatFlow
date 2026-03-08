package cs6650.assignment1;

import cs6650.assignment1.client.MessageGenerator;
import cs6650.assignment1.client.MessageSender;
import cs6650.assignment1.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Configuration
    private static final int WARMUP_THREADS = 32;
    private static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    private static final int TOTAL_MESSAGES = 500_000;
    private static final int WARMUP_TOTAL = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD;
    private static final int MAIN_PHASE_MESSAGES = TOTAL_MESSAGES - WARMUP_TOTAL;
    
    // Metrics
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger reconnectionCount = new AtomicInteger(0);
    private static final AtomicInteger totalConnections = new AtomicInteger(0);
    
    public static void main(String[] args) {
        // Parse server URL from command line or environment variable
        String serverUrl;
        if (args.length > 0) {
            serverUrl = args[0];
        } else if (System.getenv("SERVER_URL") != null) {
            serverUrl = System.getenv("SERVER_URL");
        } else {
            serverUrl = "ws://localhost:8081";
        }
        
        logger.info("========================================");
        logger.info("ChatFlow Client - Part 1");
        logger.info("========================================");
        logger.info("Server URL: {}", serverUrl);
        logger.info("Total messages to send: {}", TOTAL_MESSAGES);
        logger.info("Warmup threads: {}", WARMUP_THREADS);
        logger.info("Warmup messages per thread: {}", WARMUP_MESSAGES_PER_THREAD);
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Create message queue
            BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(TOTAL_MESSAGES + 1000);
            
            // Start message generator thread
            Thread generatorThread = new Thread(new MessageGenerator(messageQueue), "MessageGenerator");
            generatorThread.start();
            
            // Wait for some messages to be generated before starting senders
            logger.info("Waiting for initial message generation...");
            Thread.sleep(10000);
            
            // Phase 1: Warmup
            logger.info("Starting Warmup Phase...");
            long warmupStartTime = System.currentTimeMillis();
            runWarmupPhase(messageQueue, serverUrl);
            long warmupEndTime = System.currentTimeMillis();
            long warmupDuration = warmupEndTime - warmupStartTime;
            
            logger.info("Warmup Phase completed in {} ms", warmupDuration);
            logger.info("Warmup throughput: {} messages/second", 
                       (WARMUP_TOTAL * 1000.0) / warmupDuration);
            
            // Phase 2: Main Phase
            logger.info("Starting Main Phase...");
            long mainStartTime = System.currentTimeMillis();
            runMainPhase(messageQueue, serverUrl);
            long mainEndTime = System.currentTimeMillis();
            long mainDuration = mainEndTime - mainStartTime;
            
            logger.info("Main Phase completed in {} ms", mainDuration);
            
            // Wait for generator to complete
            generatorThread.join();
            
            long endTime = System.currentTimeMillis();
            long totalDuration = endTime - startTime;
            
            // Print results
            printResults(totalDuration, warmupDuration, mainDuration);
            
        } catch (Exception e) {
            logger.error("Error in main execution", e);
        }
    }
    
    private static void runWarmupPhase(BlockingQueue<ChatMessage> messageQueue, String serverUrl) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(WARMUP_THREADS);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < WARMUP_THREADS; i++) {
            totalConnections.incrementAndGet();
            MessageSender sender = new MessageSender(
                messageQueue, serverUrl, successCount, failureCount, 
                reconnectionCount, WARMUP_MESSAGES_PER_THREAD
            );
            futures.add(executor.submit(sender));
        }
        
        // Wait for all warmup threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                logger.error("Error in warmup thread", e);
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
    }
    
    private static void runMainPhase(BlockingQueue<ChatMessage> messageQueue, String serverUrl) throws InterruptedException {
        // Optimize thread count for main phase
        int optimalThreads = 64;
        int messagesPerThread = MAIN_PHASE_MESSAGES / optimalThreads;
        int remainderMessages = MAIN_PHASE_MESSAGES % optimalThreads;
        
        logger.info("Main phase using {} threads", optimalThreads);
        logger.info("Messages per thread: {}", messagesPerThread);
        
        ExecutorService executor = Executors.newFixedThreadPool(optimalThreads);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < optimalThreads; i++) {
            totalConnections.incrementAndGet();
            int messagesToSend = messagesPerThread + (i == 0 ? remainderMessages : 0);
            MessageSender sender = new MessageSender(
                messageQueue, serverUrl, successCount, failureCount, 
                reconnectionCount, messagesToSend
            );
            futures.add(executor.submit(sender));
        }
        
        // Wait for all main phase threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                logger.error("Error in main phase thread", e);
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
    }
    
    private static void printResults(long totalDuration, long warmupDuration, long mainDuration) {
        logger.info("");
        logger.info("========================================");
        logger.info("PERFORMANCE RESULTS");
        logger.info("========================================");
        logger.info("1. Successful messages sent: {}", successCount.get());
        logger.info("2. Failed messages: {}", failureCount.get());
        logger.info("3. Total runtime: {} ms ({} seconds)", totalDuration, totalDuration / 1000.0);
        logger.info("   - Warmup phase: {} ms", warmupDuration);
        logger.info("   - Main phase: {} ms", mainDuration);
        logger.info("4. Overall throughput: {} messages/second", 
                   (TOTAL_MESSAGES * 1000.0) / totalDuration);
        logger.info("   - Warmup throughput: {} messages/second", 
                   (WARMUP_TOTAL * 1000.0) / warmupDuration);
        logger.info("   - Main phase throughput: {} messages/second", 
                   (MAIN_PHASE_MESSAGES * 1000.0) / mainDuration);
        logger.info("5. Connection statistics:");
        logger.info("   - Total persistent connections: {}", totalConnections.get());
        logger.info("   - Reconnections: {}", reconnectionCount.get());
        logger.info("========================================");
    }
}
