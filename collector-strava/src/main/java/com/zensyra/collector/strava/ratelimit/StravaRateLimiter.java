package com.zensyra.collector.strava.ratelimit;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.ratelimit.TokenBucketRateLimiter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * CDI bean that enforces Strava's API rate limits.
 *
 * <p>Strava allows 100 requests per 15 minutes (900 s). This limiter
 * uses 90 % of that quota (90 requests) and refills one token every
 * 10 seconds, leaving a comfortable safety margin.
 *
 * <p>Every outbound Strava HTTP call must invoke {@link #acquire()}
 * before sending the request.
 */
@ApplicationScoped
public class StravaRateLimiter {

    /**
     * Bucket capacity — 90 % of Strava's 100 req / 15 min quota.
     */
    private static final int MAX_REQUESTS = 90;

    /**
     * Seconds between adding one token.
     * 900 s / 100 req = 9 s; using 10 s for extra headroom.
     */
    private static final long REFILL_INTERVAL_SEC = 10L;

    /** Underlying token-bucket implementation. */
    private final TokenBucketRateLimiter bucket;

    /** CDI no-arg constructor — creates the bucket with Strava limits. */
    public StravaRateLimiter() {
        this.bucket = new TokenBucketRateLimiter(MAX_REQUESTS, REFILL_INTERVAL_SEC);
    }

    /**
     * Acquires one permit before an outbound Strava request, blocking
     * if the bucket is temporarily empty.
     *
     * @throws CollectorException if the current thread is interrupted
     */
    public void acquire() {
        try {
            bucket.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CollectorException("Rate limiter interrupted", e);
        }
    }

    /**
     * Returns the number of permits currently available in the bucket.
     *
     * @return available permits (0 – {@code MAX_REQUESTS})
     */
    public int availablePermits() {
        return bucket.availablePermits();
    }

    /** Shuts down the background refill thread on bean destruction. */
    @PreDestroy
    void shutdown() {
        bucket.shutdown();
    }
}
