package cs6650.assignment1.util;

import cs6650.assignment1.model.MetricRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CsvWriter implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(CsvWriter.class);
    private final BlockingQueue<MetricRecord> metricsQueue;
    private final String outputPath;
    private final AtomicBoolean running;
    
    public CsvWriter(BlockingQueue<MetricRecord> metricsQueue, String outputPath) {
        this.metricsQueue = metricsQueue;
        this.outputPath = outputPath;
        this.running = new AtomicBoolean(true);
    }
    
    public void stop() {
        running.set(false);
    }
    
    @Override
    public void run() {
        logger.info("CSV Writer started. Output file: {}", outputPath);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("timestamp,messageType,latency,statusCode,roomId\n");
            
            int recordCount = 0;
            while (running.get() || !metricsQueue.isEmpty()) {
                MetricRecord record = metricsQueue.poll();
                if (record != null) {
                    writer.write(record.toCsvString() + "\n");
                    recordCount++;
                    
                    if (recordCount % 10000 == 0) {
                        writer.flush();
                        logger.info("Written {} records to CSV", recordCount);
                    }
                } else {
                    Thread.sleep(100);
                }
            }
            
            writer.flush();
            logger.info("CSV Writer completed. Total records: {}", recordCount);
            
        } catch (IOException e) {
            logger.error("Error writing CSV file", e);
        } catch (InterruptedException e) {
            logger.error("CSV Writer interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
