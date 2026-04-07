package cs6650.assignment1.consumerv3.db;

import cs6650.assignment1.consumerv3.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class StatsWriteWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(StatsWriteWorker.class);

    private final int workerId;
    private final BlockingQueue<QueueMessage> statsQueue;
    private final MessageRepository messageRepository;
    private final int batchSize;
    private final long flushIntervalMs;
    private volatile boolean running = true;

    public StatsWriteWorker(
        int workerId,
        BlockingQueue<QueueMessage> statsQueue,
        MessageRepository messageRepository,
        int batchSize,
        long flushIntervalMs
    ) {
        this.workerId = workerId;
        this.statsQueue = statsQueue;
        this.messageRepository = messageRepository;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void run() {
        logger.info("Stats writer {} started", workerId);
        List<QueueMessage> batch = new ArrayList<>(batchSize);

        while (running || !statsQueue.isEmpty()) {
            try {
                QueueMessage message = statsQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (message != null) {
                    batch.add(message);
                }
                statsQueue.drainTo(batch, Math.max(0, batchSize - batch.size()));

                if (!batch.isEmpty() && (batch.size() >= batchSize || message == null)) {
                    messageRepository.updateAnalyticsBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Stats writer {} failed to update analytics batch", workerId, e);
            }
        }

        if (!batch.isEmpty()) {
            try {
                messageRepository.updateAnalyticsBatch(batch);
            } catch (Exception e) {
                logger.warn("Stats writer {} failed final analytics batch", workerId, e);
            }
        }

        logger.info("Stats writer {} stopped", workerId);
    }

    public void stop() {
        running = false;
    }
}
