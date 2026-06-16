package com.zensyra.collector.core.ratelimit;

/**
 * Contracts for rate-limiting outbound HTTP requests.
 * Callers must invoke {@link #acquire()} before each request.
 */
public interface RateLimiter {

    /**
     * Acquires one permit, blocking until one is available.
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    void acquire() throws InterruptedException;

    /**
     * Returns the number of permits currently available without blocking.
     *
     * @return available permits
     */
    int availablePermits();
}
