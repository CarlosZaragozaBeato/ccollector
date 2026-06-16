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
        var limiter = new TokenBucketRateLimiter(1, 1); // refill cada 1 segundo
        limiter.acquire(); // vacía el bucket

        var acquired = new AtomicBoolean(false);
        var thread = new Thread(() -> {
            try {
                limiter.acquire(); // debe bloquearse ~1s
                acquired.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();

        // En 200ms no debería haber adquirido (bucket vacío)
        Thread.sleep(200);
        assertFalse(acquired.get());

        // En 1500ms sí debería (refill a los 1000ms)
        thread.join(1500);
        assertTrue(acquired.get());
        limiter.shutdown();
    }

    @Test
    void shouldNotExceedMaxPermits() throws InterruptedException {
        var limiter = new TokenBucketRateLimiter(3, 1);
        Thread.sleep(5000); // espera 5 ciclos de refill
        assertEquals(3, limiter.availablePermits()); // no supera el máximo
        limiter.shutdown();
    }

    @Test
    void shouldShutdownCleanly() {
        var limiter = new TokenBucketRateLimiter(5, 60);
        assertDoesNotThrow(limiter::shutdown);
    }
}
