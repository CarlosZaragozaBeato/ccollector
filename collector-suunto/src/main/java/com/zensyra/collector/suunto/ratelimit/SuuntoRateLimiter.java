package com.zensyra.collector.suunto.ratelimit;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.ratelimit.TokenBucketRateLimiter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI bean that enforces Suunto's Cloud API rate limits.
 *
 * <p>Unlike {@code StravaRateLimiter}, the limits are environment-driven
 * ({@code SUUNTO_RATELIMIT_MAX_REQUESTS} / {@code SUUNTO_RATELIMIT_REFILL_SECONDS}):
 * the Development-tier quota is unconfirmed pending verification in the
 * Suunto API Zone dashboard, and will change again on the unrestricted
 * Production tier. The defaults are deliberately conservative placeholders
 * (burst of 10, one token every 10 s ≈ 6 sustained requests/min).
 *
 * <p>Enforced automatically for every outbound Suunto HTTP request —
 * including each fault-tolerance retry attempt — via
 * {@link SuuntoRateLimitFilter} registered on {@code SuuntoApiClient}.
 */
@ApplicationScoped
public class SuuntoRateLimiter {

    /** Underlying token-bucket implementation. */
    private final TokenBucketRateLimiter bucket;

    /**
     * Creates the bucket from configuration.
     *
     * @param maxRequests           bucket capacity (maximum burst)
     * @param refillIntervalSeconds seconds between adding one token
     */
    public SuuntoRateLimiter(
            @ConfigProperty(name = "suunto.ratelimit.max-requests", defaultValue = "10")
            final int maxRequests,
            @ConfigProperty(name = "suunto.ratelimit.refill-seconds", defaultValue = "10")
            final long refillIntervalSeconds) {
        this.bucket = new TokenBucketRateLimiter(maxRequests, refillIntervalSeconds);
    }

    /**
     * Acquires one permit before an outbound Suunto request, blocking
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
     * @return available permits (0 – configured max-requests)
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
