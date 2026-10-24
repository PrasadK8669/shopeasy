package com.shopeasy.authservice.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Locks one (address + email) pair for 15 minutes after 5 wrong passwords
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();

    private record Entry(long start, int failures) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    // 0 means "go ahead". Otherwise the number of seconds the caller must wait.
    public long retryAfterSeconds(String key) {
        long now = System.currentTimeMillis();
        Entry entry = entries.get(key);
        if (entry == null) {
            return 0;
        }
        if (now - entry.start() >= WINDOW_MILLIS) {
            entries.remove(key, entry);
            return 0;
        }
        if (entry.failures() < MAX_FAILURES) {
            return 0;
        }
        return Math.max(1, (entry.start() + WINDOW_MILLIS - now + 999) / 1000);
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        if (entries.size() > 10_000) { // keep memory bounded
            entries.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MILLIS);
        }
        entries.compute(key, (k, e) ->
                e == null || now - e.start() >= WINDOW_MILLIS
                        ? new Entry(now, 1)
                        : new Entry(e.start(), e.failures() + 1));
    }

    public void recordSuccess(String key) {
        entries.remove(key);
    }
}