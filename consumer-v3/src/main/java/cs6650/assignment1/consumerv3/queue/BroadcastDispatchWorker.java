package cs6650.assignment1.consumerv3.queue;

import cs6650.assignment1.consumerv3.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BroadcastDispatchWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastDispatchWorker.class);

    private final int workerId;
    private final BlockingQueue<QueueMessage> broadcastQueue;
    private final ServerBroadcastClient broadcastClient;
    private final Semaphore inFlightLimiter;
    private final int batchSize;
    private final long flushIntervalMs;
    private volatile boolean running = true;

    public BroadcastDispatchWorker(
        int workerId,
        BlockingQueue<QueueMessage> broadcastQueue,
        ServerBroadcastClient broadcastClient,
        int batchSize,
        long flushIntervalMs
    ) {
        this.workerId = workerId;
        this.broadcastQueue = broadcastQueue;
        this.broadcastClient = broadcastClient;
        this.inFlightLimiter = new Semaphore(Math.max(1, batchSize * 4));
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void run() {
        logger.info("Broadcast worker {} started", workerId);
        List<QueueMessage> batch = new ArrayList<>(batchSize);

        while (running || !broadcastQueue.isEmpty()) {
            try {
                QueueMessage message = broadcastQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (message != null) {
                    batch.add(message);
                }
                broadcastQueue.drainTo(batch, Math.max(0, batchSize - batch.size()));

                if (!batch.isEmpty() && (batch.size() >= batchSize || message == null)) {
                    dispatchBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Broadcast worker {} failed to send batch", workerId, e);
            }
        }

        if (!batch.isEmpty()) {
            try {
                dispatchBatch(batch);
            } catch (Exception e) {
                logger.warn("Broadcast worker {} failed final batch", workerId, e);
            }
        }

        logger.info("Broadcast worker {} stopped", workerId);
    }

    public void stop() {
        running = false;
    }

    private void dispatchBatch(List<QueueMessage> batch) throws InterruptedException {
        for (QueueMessage message : batch) {
            inFlightLimiter.acquire();
            CompletableFuture<Void> future = broadcastClient.broadcastSingleAsync(message);
            future.whenComplete((ignored, throwable) -> inFlightLimiter.release());
        }
    }
}
