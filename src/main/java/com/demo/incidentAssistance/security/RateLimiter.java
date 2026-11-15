package com.demo.incidentAssistance.security;

public class RateLimiter {
    private final int capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefill = System.nanoTime();

    public RateLimiter(int perMinute) {
        this.capacity = perMinute;
        this.refillPerNano = perMinute / 60_000_000_000.0;
        this.tokens = perMinute;
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    public synchronized long retryAfterSeconds() {
        refill();
        return (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000_000.0);
    }

    private void refill() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
    }
}
