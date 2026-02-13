package cs6650.assignment1;

import cs6650.assignment1.client.MessageGenerator;
import cs6650.assignment1.client.MessageSender;
import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.MetricRecord;
import cs6650.assignment1.util.CsvWriter;
import cs6650.assignment1.util.PerformanceAnalyzer;
import cs6650.assignment1.util.ThroughputVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    
    // Server URL - CHANGE THIS TO YOUR SERVER URL
    private static final String SERVER_URL = "ws://localhost:8080";
    
    // Metrics
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger reconnectionCount = new AtomicInteger(0);
    private static final AtomicInteger totalConnections = new AtomicInteger(0);
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("ChatFlow Client - Part 2 (Performance Analysis)");
        logger.info("========================================");
        logger.info("Server URL: {}", SERVER_URL);
        logger.info("Total messages to send: {}", TOTAL_MESSAGES);
        logger.info("Warmup threads: {}", WARMUP_THREADS);
        logger.info("Warmup messages per thread: {}", WARMUP_MESSAGES_PER_THREAD);
        logger.info("========================================");
        
        // Create results directory
        File resultsDir = new File("results");
        if (!resultsDir.exists()) {
            resultsDir.mkdir();
        }
        
        // Generate timestamp for output files
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String csvFilePath = "results/metrics_" + timestamp + ".csv";
        String chartFilePath = "results/throughput_" + timestamp + ".txt";
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Create message queue
            BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(TOTAL_MESSAGES + 1000);
            
            // Create metrics queue
            BlockingQueue<MetricRecord> metricsQueue = new LinkedBlockingQueue<>();
            
            // Start CSV writer thread
            CsvWriter csvWriter = new CsvWriter(metricsQueue, csvFilePath);
            Thread csvWriterThread = new Thread(csvWriter, "CsvWriter");
            csvWriterThread.start();
            
            // Start message generator thread
            Thread generatorThread = new Thread(new MessageGenerator(messageQueue), "MessageGenerator");
            generatorThread.start();
            
            // Wait for some messages to be generated before starting senders
            logger.info("Waiting for initial message generation...");
            Thread.sleep(2000);
            
            // Phase 1: Warmup
            logger.info("Starting Warmup Phase...");
            long warmupStartTime = System.currentTimeMillis();
            runWarmupPhase(messageQueue, metricsQueue);
            long warmupEndTime = System.currentTimeMillis();
            long warmupDuration = warmupEndTime - warmupStartTime;
            
            logger.info("Warmup Phase completed in {} ms", warmupDuration);
            logger.info("Warmup throughput: {} messages/second", 
                       (WARMUP_TOTAL * 1000.0) / warmupDuration);
            
            // Phase 2: Main Phase
            logger.info("Starting Main Phase...");
            long mainStartTime = System.currentTimeMillis();
            runMainPhase(messageQueue, metricsQueue);
            long mainEndTime = System.currentTimeMillis();
            long mainDuration = mainEndTime - mainStartTime;
            
            logger.info("Main Phase completed in {} ms", mainDuration);
            
            // Wait for generator to complete
            generatorThread.join();
            
            // Stop CSV writer and wait for it to finish
            logger.info("Waiting for CSV writer to complete...");
            Thread.sleep(2000); // Give time for remaining metrics to be written
            csvWriter.stop();
            csvWriterThread.join(10000);
            
            long endTime = System.currentTimeMillis();
            long totalDuration = endTime - startTime;
            
            // Print basic results
            printResults(totalDuration, warmupDuration, mainDuration);
            
            // Perform statistical analysis
            logger.info("\nPerforming statistical analysis...");
            PerformanceAnalyzer.Statistics stats = PerformanceAnalyzer.analyzeMetrics(csvFilePath);
            if (stats != null) {
                System.out.println(stats.toString());
            }
            
            // Calculate and visualize throughput over time
            logger.info("Calculating throughput over time...");
            Map<Long, Integer> throughputData = PerformanceAnalyzer.calculateThroughputOverTime(csvFilePath, 10);
            
            if (!throughputData.isEmpty()) {
                ThroughputVisualizer.saveChartAsText(throughputData, chartFilePath);
                logger.info("Throughput data saved to: {}", chartFilePath);
                
                // Display visual chart
                ThroughputVisualizer.createThroughputChart(throughputData, "ChatFlow Throughput Analysis");
            }
            
            logger.info("\n========================================");
            logger.info("RESULTS SAVED:");
            logger.info("  - Metrics CSV: {}", csvFilePath);
            logger.info("  - Throughput Chart: {}", chartFilePath);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("Error in main execution", e);
        }
    }
    
    private static void runWarmupPhase(BlockingQueue<ChatMessage> messageQueue,
                                      BlockingQueue<MetricRecord> metricsQueue) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(WARMUP_THREADS);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < WARMUP_THREADS; i++) {
            totalConnections.incrementAndGet();
            MessageSender sender = new MessageSender(
                messageQueue, SERVER_URL, successCount, failureCount, 
                reconnectionCount, WARMUP_MESSAGES_PER_THREAD, metricsQueue
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
    
    private static void runMainPhase(BlockingQueue<ChatMessage> messageQueue,
                                    BlockingQueue<MetricRecord> metricsQueue) throws InterruptedException {
        // Optimize thread count for main phase
        int optimalThreads = Runtime.getRuntime().availableProcessors() * 4;
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
                messageQueue, SERVER_URL, successCount, failureCount, 
                reconnectionCount, messagesToSend, metricsQueue
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
        executor.awaitTermination(10, TimeUnit.MINUTES);
    }
    
    private static void printResults(long totalDuration, long warmupDuration, long mainDuration) {
        logger.info("");
        logger.info("========================================");
        logger.info("BASIC PERFORMANCE RESULTS");
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
        logger.info("   - Total connections: {}", totalConnections.get());
        logger.info("   - Reconnections: {}", reconnectionCount.get());
        logger.info("========================================");
    }
}