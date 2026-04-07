package cs6650.assignment1.consumerv3.db;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
    private final int failureThreshold;
    private final long openDurationMs;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilEpochMs = new AtomicLong();

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1000L, openDurationMs);
    }

    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        long openUntil = openUntilEpochMs.get();
        return openUntil == 0 || now >= openUntil;
    }

    public long getRemainingOpenMs() {
        long remaining = openUntilEpochMs.get() - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntilEpochMs.set(0);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openUntilEpochMs.set(System.currentTimeMillis() + openDurationMs);
        }
    }
}
