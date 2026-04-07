package cs6650.assignment1.consumerv3.model;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

public class PersistenceStats {
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dlq = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong queueFull = new AtomicLong();
    private final IntSupplier queueDepthSupplier;
    private final IntSupplier statsQueueDepthSupplier;

    public PersistenceStats(IntSupplier queueDepthSupplier, IntSupplier statsQueueDepthSupplier) {
        this.queueDepthSupplier = queueDepthSupplier;
        this.statsQueueDepthSupplier = statsQueueDepthSupplier;
    }

    public void incReceived() { received.incrementAndGet(); }
    public void incPersisted(long delta) { persisted.addAndGet(delta); }
    public void incDuplicates(long delta) { duplicates.addAndGet(delta); }
    public void incFailed(long delta) { failed.addAndGet(delta); }
    public void incDlq(long delta) { dlq.addAndGet(delta); }
    public void incRetries() { retries.incrementAndGet(); }
    public void incQueueFull() { queueFull.incrementAndGet(); }

    public Map<String, Object> snapshot() {
        return Map.of(
            "messagesReceived", received.get(),
            "messagesPersisted", persisted.get(),
            "duplicateMessagesSkipped", duplicates.get(),
            "messagesFailed", failed.get(),
            "messagesToDlq", dlq.get(),
            "retryAttempts", retries.get(),
            "persistQueueFull", queueFull.get(),
            "persistQueueDepth", queueDepthSupplier.getAsInt(),
            "statsQueueDepth", statsQueueDepthSupplier.getAsInt()
        );
    }
}
