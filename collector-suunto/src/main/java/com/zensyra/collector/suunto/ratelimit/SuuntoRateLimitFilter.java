package com.zensyra.collector.suunto.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * JAX-RS client filter that acquires one rate-limiter permit per outbound
 * HTTP request. Registered on {@code SuuntoApiClient} via
 * {@code @RegisterProvider}, so every physical call to the Suunto Cloud API
 * is throttled automatically — including each {@code @Retry} attempt, which
 * re-enters this filter and therefore consumes its own permit. Callers (the
 * sync jobs) never invoke the limiter manually.
 *
 * <p>Blocking here is safe: the REST client is synchronous and runs on the
 * caller's worker thread, the same semantics as Strava's job-side acquire.
 */
@ApplicationScoped
public class SuuntoRateLimitFilter implements ClientRequestFilter {

    @Inject
    SuuntoRateLimiter rateLimiter;

    @Override
    public void filter(final ClientRequestContext requestContext) {
        rateLimiter.acquire();
    }
}
