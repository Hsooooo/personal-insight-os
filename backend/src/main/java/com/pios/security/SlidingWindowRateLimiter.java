package com.pios.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Small in-memory sliding-window counter (single-instance deployment). */
public class SlidingWindowRateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    public SlidingWindowRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Records a hit if under the limit; returns false (and records nothing) when the limit is reached. */
    public boolean tryAcquire(String key) {
        Deque<Long> deque = deque(key);
        synchronized (deque) {
            long now = clock.getAsLong();
            evict(deque, now);
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public void record(String key) {
        Deque<Long> deque = deque(key);
        synchronized (deque) {
            long now = clock.getAsLong();
            evict(deque, now);
            deque.addLast(now);
        }
    }

    public boolean isLimited(String key) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            evict(deque, clock.getAsLong());
            return deque.size() >= limit;
        }
    }

    /** Seconds until the oldest hit in the window expires (≥1). */
    public long retryAfterSeconds(String key) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return 1;
        }
        synchronized (deque) {
            Long oldest = deque.peekFirst();
            if (oldest == null) {
                return 1;
            }
            long remaining = oldest + windowMillis - clock.getAsLong();
            return Math.max(1, (remaining + 999) / 1000);
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    public void prune() {
        long now = clock.getAsLong();
        hits.entrySet().removeIf(e -> {
            Deque<Long> deque = e.getValue();
            synchronized (deque) {
                evict(deque, now);
                return deque.isEmpty();
            }
        });
    }

    private Deque<Long> deque(String key) {
        if (hits.size() > MAX_KEYS) {
            prune();
        }
        return hits.computeIfAbsent(key, k -> new ArrayDeque<>());
    }

    private void evict(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && deque.peekFirst() <= now - windowMillis) {
            deque.pollFirst();
        }
    }
}
