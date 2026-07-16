package com.zensyra.collector.suunto.ratelimit;

import com.zensyra.collector.core.exception.CollectorException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit tests of the config-driven wrapper, mirroring the timing-based
 * approach of core's TokenBucketRateLimiterTest: they prove the limiter
 * actually blocks when the configured limit is reached and unblocks on
 * refill — not merely that the class compiles.
 */
class SuuntoRateLimiterTest {

    @Test
    void startsWithTheConfiguredBurstCapacity() {
        var limiter = new SuuntoRateLimiter(5, 60);
        assertEquals(5, limiter.availablePermits());
        limiter.shutdown();
    }

    @Test
    void blocksWhenConfiguredLimitIsReachedAndUnblocksOnRefill() throws Exception {
        var limiter = new SuuntoRateLimiter(1, 1); // capacity 1, refill every second
        limiter.acquire(); // empty the bucket

        var acquired = new AtomicBoolean(false);
        var thread = new Thread(() -> {
            limiter.acquire(); // should block for ~1 second
            acquired.set(true);
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
    void interruptionWhileBlockedSurfacesAsCollectorException() throws Exception {
        var limiter = new SuuntoRateLimiter(1, 3600); // no refill within the test
        limiter.acquire(); // empty the bucket

        var thrown = new AtomicReference<RuntimeException>();
        var thread = new Thread(() -> {
            try {
                limiter.acquire();
            } catch (RuntimeException e) {
                thrown.set(e);
            }
        });
        thread.start();
        Thread.sleep(200); // let it block on the empty bucket
        thread.interrupt();
        thread.join(1000);

        assertInstanceOf(CollectorException.class, thrown.get());
        limiter.shutdown();
    }
}
