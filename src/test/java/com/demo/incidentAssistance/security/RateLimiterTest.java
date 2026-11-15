package com.demo.incidentAssistance.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RateLimiterTest {
    @Test
    void allowsABurstUpToCapacityThenRejects() {
        RateLimiter limiter = new RateLimiter(3);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void reportsAPositiveRetryAfterWhenEmpty() {
        RateLimiter limiter = new RateLimiter(60);
        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire();
        }

        assertFalse(limiter.tryAcquire());
        assertTrue(limiter.retryAfterSeconds() >= 1);
    }
}
