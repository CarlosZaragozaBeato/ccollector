package com.zensyra.collector.core.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    @Test
    void shouldStartWithFullBucket() {
        var limiter = new TokenBucketRateLimiter(5, 60);
        assertEquals(5, limiter.availablePermits());
        limiter.shutdown();
    }

    @Test
    void shouldConsumePermits() throws InterruptedException {
        var limiter = new TokenBucketRateLimiter(5, 60);
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        assertEquals(2, limiter.availablePermits());
        limiter.shutdown();
    }

    @Test
    void shouldBlockWhenEmpty() throws Exception {
        var limiter = new TokenBucketRateLimiter(1, 1); // refill every second
        limiter.acquire(); // empty the bucket

        var acquired = new AtomicBoolean(false);
        var thread = new Thread(() -> {
            try {
                limiter.acquire(); // should block for ~1 second
                acquired.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();

        // It should not have acquired within 200 ms (empty bucket).
        Thread.sleep(200);
        assertFalse(acquired.get());

        // It should have acquired within 1,500 ms (refill at 1,000 ms).
        thread.join(1500);
        assertTrue(acquired.get());
        limiter.shutdown();
    }

    @Test
    void shouldNotExceedMaxPermits() throws InterruptedException {
        var limiter = new TokenBucketRateLimiter(3, 1);
        Thread.sleep(5000); // wait for five refill cycles
        assertEquals(3, limiter.availablePermits()); // does not exceed the maximum
        limiter.shutdown();
    }

    @Test
    void shouldShutdownCleanly() {
        var limiter = new TokenBucketRateLimiter(5, 60);
        assertDoesNotThrow(limiter::shutdown);
    }
}
