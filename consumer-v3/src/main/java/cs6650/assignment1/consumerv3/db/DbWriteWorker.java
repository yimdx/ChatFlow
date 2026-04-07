package cs6650.assignment1.consumerv3.db;

import cs6650.assignment1.consumerv3.model.PersistenceStats;
import cs6650.assignment1.consumerv3.model.QueueMessage;
import cs6650.assignment1.consumerv3.queue.RabbitMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DbWriteWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DbWriteWorker.class);

    private final int workerId;
    private final BlockingQueue<QueueMessage> persistQueue;
    private final MessageRepository messageRepository;
    private final PersistenceStats stats;
    private final BlockingQueue<QueueMessage> statsQueue;
    private final int batchSize;
    private final long flushIntervalMs;
    private final int maxRetries;
    private final long retryBaseMs;
    private final CircuitBreaker circuitBreaker;
    private final RabbitMQConnection rabbitMQConnection;
    private final String dlqName;
    private volatile boolean running = true;

    public DbWriteWorker(
        int workerId,
        BlockingQueue<QueueMessage> persistQueue,
        MessageRepository messageRepository,
        PersistenceStats stats,
        BlockingQueue<QueueMessage> statsQueue,
        int batchSize,
        long flushIntervalMs,
        int maxRetries,
        long retryBaseMs,
        CircuitBreaker circuitBreaker,
        RabbitMQConnection rabbitMQConnection,
        String dlqName
    ) {
        this.workerId = workerId;
        this.persistQueue = persistQueue;
        this.messageRepository = messageRepository;
        this.stats = stats;
        this.statsQueue = statsQueue;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.maxRetries = maxRetries;
        this.retryBaseMs = retryBaseMs;
        this.circuitBreaker = circuitBreaker;
        this.rabbitMQConnection = rabbitMQConnection;
        this.dlqName = dlqName;
    }

    @Override
    public void run() {
        logger.info("DB writer {} started", workerId);
        List<QueueMessage> batch = new ArrayList<>(batchSize);

        while (running || !persistQueue.isEmpty()) {
            try {
                QueueMessage message = persistQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (message != null) {
                    batch.add(message);
                }
                persistQueue.drainTo(batch, Math.max(0, batchSize - batch.size()));

                if (!batch.isEmpty() && (batch.size() >= batchSize || message == null)) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
        logger.info("DB writer {} stopped", workerId);
    }

    public void stop() {
        running = false;
    }

    private void flushBatch(List<QueueMessage> batch) {
        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                if (!circuitBreaker.allowRequest()) {
                    Thread.sleep(circuitBreaker.getRemainingOpenMs());
                }
                List<QueueMessage> insertedMessages = messageRepository.persistBatch(batch);
                circuitBreaker.recordSuccess();
                stats.incPersisted(insertedMessages.size());
                if (insertedMessages.size() < batch.size()) {
                    stats.incDuplicates(batch.size() - insertedMessages.size());
                }
                for (QueueMessage insertedMessage : insertedMessages) {
                    if (!statsQueue.offer(insertedMessage)) {
                        logger.warn("Stats queue full, analytics update deferred for message {}", insertedMessage.getMessageId());
                    }
                }
                return;
            } catch (Exception e) {
                attempt++;
                circuitBreaker.recordFailure();
                if (attempt > maxRetries) {
                    logger.error("DB writer {} failed batch after {} attempts, moving to DLQ", workerId, maxRetries, e);
                    stats.incFailed(batch.size());
                    for (QueueMessage msg : batch) {
                        rabbitMQConnection.publishToDlq(dlqName, dlqName, toJsonBytes(msg));
                    }
                    stats.incDlq(batch.size());
                    return;
                }

                stats.incRetries();
                long backoff = retryBaseMs * (1L << Math.min(attempt, 10));
                logger.warn("DB writer {} batch failed (attempt {}), retrying in {} ms", workerId, attempt, backoff, e);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static byte[] toJsonBytes(QueueMessage msg) {
        String json = String.format(
            "{\"messageId\":\"%s\",\"roomId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"message\":\"%s\"}",
            escape(msg.getMessageId()),
            escape(msg.getRoomId()),
            escape(msg.getUserId()),
            escape(msg.getUsername()),
            escape(msg.getMessage())
        );
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
