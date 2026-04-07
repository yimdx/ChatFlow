package cs6650.assignment1.api;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ResponseCache {
    private final long ttlMs;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ResponseCache(Duration ttl) {
        this.ttlMs = Math.max(0L, ttl.toMillis());
    }

    public byte[] getOrCompute(String key, Supplier<byte[]> producer) {
        if (ttlMs == 0) {
            return producer.get();
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAtMs >= now) {
            return cached.payload;
        }

        byte[] payload = producer.get();
        cache.put(key, new CacheEntry(payload, now + ttlMs));
        return payload;
    }

    private record CacheEntry(byte[] payload, long expiresAtMs) {
    }
}
