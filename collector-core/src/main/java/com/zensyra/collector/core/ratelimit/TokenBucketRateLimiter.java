package com.zensyra.collector.core.ratelimit;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiter backed by a {@link Semaphore}.
 *
 * <p>The bucket starts full ({@code maxRequests} permits). A background
 * thread adds one permit every {@code refillIntervalSeconds}, capped at
 * the bucket capacity. Callers block on {@link #acquire()} when the
 * bucket is empty.
 *
 * <p>This class is not a CDI bean; it is instantiated by integration-
 * specific wrappers (e.g. {@code StravaRateLimiter}).
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    /** Maximum number of permits (bucket capacity). */
    private final int maxPermits;

    /** Semaphore backing the permit pool. */
    private final Semaphore semaphore;

    /** Scheduler that refills the bucket at a fixed rate. */
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new token-bucket rate limiter.
     *
     * @param maxRequests          bucket capacity and initial permits
     * @param refillIntervalSeconds seconds between adding one permit
     */
    public TokenBucketRateLimiter(
            final int maxRequests,
            final long refillIntervalSeconds) {

        this.maxPermits = maxRequests;
        this.semaphore = new Semaphore(maxRequests);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-refill");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            () -> {
                if (semaphore.availablePermits() < maxPermits) {
                    semaphore.release();
                }
            },
            refillIntervalSeconds,
            refillIntervalSeconds,
            TimeUnit.SECONDS);
    }

    /**
     * Acquires one permit, blocking until one is available.
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    @Override
    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    /**
     * Returns the number of permits currently available.
     *
     * @return available permits
     */
    @Override
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Shuts down the background refill scheduler.
     * Call this when the owning bean is destroyed.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
