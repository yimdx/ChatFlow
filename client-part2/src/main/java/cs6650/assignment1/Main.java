package cs6650.assignment1;

import cs6650.assignment1.client.MessageGenerator;
import cs6650.assignment1.client.MessageSender;
import cs6650.assignment1.model.ChatMessage;
import cs6650.assignment1.model.MetricRecord;
import cs6650.assignment1.util.CsvWriter;
import cs6650.assignment1.util.PerformanceAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

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
        
        int warmupThreads = getEnvInt("WARMUP_THREADS", 32);
        int warmupMessagesPerThread = getEnvInt("WARMUP_MESSAGES_PER_THREAD", 1000);
        int totalMessages = getEnvInt("TOTAL_MESSAGES", 500_000);
        int warmupTotal = warmupThreads * warmupMessagesPerThread;
        if (totalMessages < warmupTotal) {
            totalMessages = warmupTotal;
        }
        int mainPhaseMessages = totalMessages - warmupTotal;
        int mainPhaseThreads = getEnvInt("MAIN_PHASE_THREADS", 32);
        boolean warmupOnly = getEnvBoolean("WARMUP_ONLY", false);

        logger.info("========================================");
        logger.info("ChatFlow Client - Part 2 (Performance Analysis)");
        logger.info("========================================");
        logger.info("Server URL: {}", serverUrl);
        logger.info("Total messages to send: {}", totalMessages);
        logger.info("Warmup threads: {}", warmupThreads);
        logger.info("Warmup messages per thread: {}", warmupMessagesPerThread);
        logger.info("Main phase threads: {}", mainPhaseThreads);
        logger.info("Warmup only mode: {}", warmupOnly);
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
            BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(totalMessages + 1000);
            
            // Create metrics queue
            BlockingQueue<MetricRecord> metricsQueue = new LinkedBlockingQueue<>();
            
            // Start CSV writer thread
            CsvWriter csvWriter = new CsvWriter(metricsQueue, csvFilePath);
            Thread csvWriterThread = new Thread(csvWriter, "CsvWriter");
            csvWriterThread.start();
            
            // Start message generator thread
            Thread generatorThread = new Thread(new MessageGenerator(messageQueue, totalMessages), "MessageGenerator");
            generatorThread.start();
            
            // Wait for some messages to be generated before starting senders
            logger.info("Waiting for initial message generation...");
            Thread.sleep(2000);
            
            // Phase 1: Warmup
            logger.info("Starting Warmup Phase...");
            long warmupStartTime = System.currentTimeMillis();
            runWarmupPhase(messageQueue, metricsQueue, serverUrl, warmupThreads, warmupMessagesPerThread);
            long warmupEndTime = System.currentTimeMillis();
            long warmupDuration = warmupEndTime - warmupStartTime;
            
            logger.info("Warmup Phase completed in {} ms", warmupDuration);
            logger.info("Warmup throughput: {} messages/second", 
                       (warmupTotal * 1000.0) / warmupDuration);

            long mainDuration = 0L;
            if (!warmupOnly && mainPhaseMessages > 0) {
                logger.info("Starting Main Phase...");
                long mainStartTime = System.currentTimeMillis();
                runMainPhase(messageQueue, metricsQueue, serverUrl, mainPhaseMessages, mainPhaseThreads);
                long mainEndTime = System.currentTimeMillis();
                mainDuration = mainEndTime - mainStartTime;

                logger.info("Main Phase completed in {} ms", mainDuration);
            } else {
                logger.info("Skipping Main Phase");
            }
            
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
            printResults(totalDuration, warmupDuration, mainDuration, totalMessages, warmupTotal, mainPhaseMessages);
            
            // Perform statistical analysis
            logger.info("\nPerforming statistical analysis...");
            PerformanceAnalyzer.Statistics stats = PerformanceAnalyzer.analyzeMetrics(csvFilePath);
            if (stats != null) {
                System.out.println(stats.toString());
            }

            // Call the server-v2 metrics API after the test ends
            String metricsApiBase = getMetricsApiBase(serverUrl);
            logger.info("\nCalling metrics API after test completion...");
            logger.info("Metrics API base: {}", metricsApiBase);
            String metricsJson = fetchMetricsSummary(metricsApiBase, startTime, endTime);
            if (metricsJson != null) {
                logger.info("\n========================================");
                logger.info("SERVER-V2 METRICS API RESPONSE");
                logger.info("========================================");
                logger.info("{}", metricsJson);
                logger.info("========================================");
            }
            
            // Calculate throughput over time
            logger.info("Calculating throughput over time...");
            Map<Long, Integer> throughputData = PerformanceAnalyzer.calculateThroughputOverTime(csvFilePath, 10);
            
            if (!throughputData.isEmpty()) {
                PerformanceAnalyzer.saveThroughputData(throughputData, chartFilePath);
                logger.info("Throughput data saved to: {}", chartFilePath);
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
                                      BlockingQueue<MetricRecord> metricsQueue,
                                      String serverUrl,
                                      int warmupThreads,
                                      int warmupMessagesPerThread) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(warmupThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < warmupThreads; i++) {
            totalConnections.incrementAndGet();
            MessageSender sender = new MessageSender(
                messageQueue, serverUrl, successCount, failureCount, 
                reconnectionCount, warmupMessagesPerThread, metricsQueue
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
                                    BlockingQueue<MetricRecord> metricsQueue,
                                    String serverUrl,
                                    int mainPhaseMessages,
                                    int mainPhaseThreads) throws InterruptedException {
        int effectiveThreads = Math.max(1, mainPhaseThreads);
        int messagesPerThread = mainPhaseMessages / effectiveThreads;
        int remainderMessages = mainPhaseMessages % effectiveThreads;

        logger.info("Main phase using {} threads", effectiveThreads);
        logger.info("Messages per thread: {}", messagesPerThread);

        ExecutorService executor = Executors.newFixedThreadPool(effectiveThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < effectiveThreads; i++) {
            totalConnections.incrementAndGet();
            int messagesToSend = messagesPerThread + (i == 0 ? remainderMessages : 0);
            MessageSender sender = new MessageSender(
                messageQueue, serverUrl, successCount, failureCount, 
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
    
    private static void printResults(long totalDuration, long warmupDuration, long mainDuration,
                                     int totalMessages, int warmupTotal, int mainPhaseMessages) {
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
                   (totalMessages * 1000.0) / totalDuration);
        logger.info("   - Warmup throughput: {} messages/second", 
                   (warmupTotal * 1000.0) / warmupDuration);
        if (mainDuration > 0 && mainPhaseMessages > 0) {
            logger.info("   - Main phase throughput: {} messages/second",
                (mainPhaseMessages * 1000.0) / mainDuration);
        } else {
            logger.info("   - Main phase throughput: skipped");
        }
        logger.info("5. Connection statistics:");
        logger.info("   - Total persistent connections: {}", totalConnections.get());
        logger.info("   - Reconnections: {}", reconnectionCount.get());
        logger.info("========================================");
    }

    private static String getMetricsApiBase(String serverUrl) {
        String envOverride = System.getenv("METRICS_API_URL");
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride;
        }

        try {
            URI uri = URI.create(serverUrl);
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            return "http://" + host + ":8083";
        } catch (Exception e) {
            return "http://localhost:8083";
        }
    }

    private static String fetchMetricsSummary(String metricsApiBase, long startTime, long endTime) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

            String start = java.time.Instant.ofEpochMilli(startTime).toString();
            String end = java.time.Instant.ofEpochMilli(endTime).toString();
            String url = metricsApiBase + "/metrics?start=" + start + "&end=" + end;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }

            logger.warn("Metrics API returned status {}", response.statusCode());
            return response.body();
        } catch (Exception e) {
            logger.error("Failed to fetch metrics summary from server-v2", e);
            return null;
        }
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
