package cs6650.assignment1.util;

import cs6650.assignment1.model.MetricRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class PerformanceAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceAnalyzer.class);
    
    public static class Statistics {
        public double meanLatency;
        public double medianLatency;
        public double p95Latency;
        public double p99Latency;
        public long minLatency;
        public long maxLatency;
        public Map<String, Integer> messageTypeDistribution;
        public Map<Integer, Integer> messageCountPerRoom;
        public Map<Integer, Double> throughputPerRoom;
        public int totalMessages;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================\n");
            sb.append("STATISTICAL ANALYSIS\n");
            sb.append("========================================\n");
            sb.append(String.format("Total Messages: %d\n", totalMessages));
            sb.append(String.format("Mean Response Time: %.2f ms\n", meanLatency));
            sb.append(String.format("Median Response Time: %.2f ms\n", medianLatency));
            sb.append(String.format("95th Percentile: %.2f ms\n", p95Latency));
            sb.append(String.format("99th Percentile: %.2f ms\n", p99Latency));
            sb.append(String.format("Min Response Time: %d ms\n", minLatency));
            sb.append(String.format("Max Response Time: %d ms\n", maxLatency));
            sb.append("\nMessage Type Distribution:\n");
            messageTypeDistribution.forEach((type, count) -> 
                sb.append(String.format("  %s: %d (%.1f%%)\n", type, count, 
                    100.0 * count / totalMessages)));
            sb.append("\nMessage Count Per Room:\n");
            messageCountPerRoom.forEach((room, count) -> 
                sb.append(String.format("  Room %d: %d messages\n", room, count)));
            sb.append("\nThroughput Per Room:\n");
            throughputPerRoom.forEach((room, throughput) -> 
                sb.append(String.format("  Room %d: %.2f messages/second\n", room, throughput)));
            sb.append("========================================\n");
            return sb.toString();
        }
    }
    
    public static Statistics analyzeMetrics(String csvFilePath) {
        logger.info("Analyzing metrics from: {}", csvFilePath);
        
        List<Long> latencies = new ArrayList<>();
        Map<String, Integer> messageTypeCount = new HashMap<>();
        Map<Integer, Integer> roomCount = new HashMap<>();
        Map<Integer, Long> roomFirstTimestamp = new HashMap<>();
        Map<Integer, Long> roomLastTimestamp = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // Skip header
            reader.readLine();
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    try {
                        long timestamp = Long.parseLong(parts[0]);
                        long latency = Long.parseLong(parts[2]);
                        String messageType = parts[1];
                        int roomId = Integer.parseInt(parts[4]);
                        
                        latencies.add(latency);
                        messageTypeCount.put(messageType, messageTypeCount.getOrDefault(messageType, 0) + 1);
                        roomCount.put(roomId, roomCount.getOrDefault(roomId, 0) + 1);
                        
                        // Track timestamps per room
                        roomFirstTimestamp.put(roomId, 
                            Math.min(roomFirstTimestamp.getOrDefault(roomId, Long.MAX_VALUE), timestamp));
                        roomLastTimestamp.put(roomId, 
                            Math.max(roomLastTimestamp.getOrDefault(roomId, Long.MIN_VALUE), timestamp));
                    } catch (NumberFormatException e) {
                        logger.warn("Skipping invalid line: {}", line);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading CSV file", e);
            return null;
        }
        
        if (latencies.isEmpty()) {
            logger.error("No valid data found in CSV");
            return null;
        }
        
        // Sort latencies for percentile calculations
        Collections.sort(latencies);
        
        Statistics stats = new Statistics();
        stats.totalMessages = latencies.size();
        
        // Calculate mean
        stats.meanLatency = latencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        // Calculate median
        stats.medianLatency = getPercentile(latencies, 50);
        
        // Calculate percentiles
        stats.p95Latency = getPercentile(latencies, 95);
        stats.p99Latency = getPercentile(latencies, 99);
        
        // Min and max
        stats.minLatency = latencies.get(0);
        stats.maxLatency = latencies.get(latencies.size() - 1);
        
        // Distributions
        stats.messageTypeDistribution = messageTypeCount;
        stats.messageCountPerRoom = roomCount;
        
                // Calculate throughput per room (messages/second)
        stats.throughputPerRoom = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : roomCount.entrySet()) {
            int roomId = entry.getKey();
            int count = entry.getValue();
            long firstTs = roomFirstTimestamp.get(roomId);
            long lastTs = roomLastTimestamp.get(roomId);
            double durationSeconds = (lastTs - firstTs) / 1000.0;
            
            // Avoid division by zero
            double throughput = durationSeconds > 0 ? count / durationSeconds : count;
            stats.throughputPerRoom.put(roomId, throughput);
        }

        logger.info("Analysis completed");
        return stats;
    }
    
    private static double getPercentile(List<Long> sortedList, int percentile) {
        if (sortedList.isEmpty()) return 0.0;
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        
        return sortedList.get(index);
    }
    
    public static Map<Long, Integer> calculateThroughputOverTime(String csvFilePath, int bucketSizeSeconds) {
        logger.info("Calculating throughput over time with {}-second buckets", bucketSizeSeconds);
        
        Map<Long, Integer> throughputBuckets = new TreeMap<>();
        long minTimestamp = Long.MAX_VALUE;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // Skip header
            reader.readLine();
            
            // First pass: find minimum timestamp
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 1) {
                    try {
                        long timestamp = Long.parseLong(parts[0]);
                        minTimestamp = Math.min(minTimestamp, timestamp);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading CSV file", e);
            return throughputBuckets;
        }
        
        // Second pass: bucket the data
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // Skip header
            reader.readLine();
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 1) {
                    try {
                        long timestamp = Long.parseLong(parts[0]);
                        long elapsedSeconds = (timestamp - minTimestamp) / 1000;
                        long bucketKey = (elapsedSeconds / bucketSizeSeconds) * bucketSizeSeconds;
                        
                        throughputBuckets.put(bucketKey, throughputBuckets.getOrDefault(bucketKey, 0) + 1);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading CSV file", e);
        }
        
        logger.info("Throughput calculation completed. {} buckets created", throughputBuckets.size());
        return throughputBuckets;
    }
    
    /**
     * Save throughput data to a text file for visualization
     */
    public static void saveThroughputData(Map<Long, Integer> throughputData, String outputPath) {
        logger.info("Saving throughput data to: {}", outputPath);
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(outputPath)) {
            writer.println("Throughput Over Time");
            writer.println("====================");
            writer.println("Time (seconds), Messages/Second");
            
            for (Map.Entry<Long, Integer> entry : throughputData.entrySet()) {
                double messagesPerSecond = entry.getValue() / 10.0; // Assuming 10-second buckets
                writer.printf("%d, %.2f%n", entry.getKey(), messagesPerSecond);
            }
            
            logger.info("Throughput data saved to text file");
        } catch (java.io.IOException e) {
            logger.error("Error saving throughput data", e);
        }
    }
}
