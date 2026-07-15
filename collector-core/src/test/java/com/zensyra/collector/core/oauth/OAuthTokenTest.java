package com.zensyra.collector.core.oauth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary tests for the refresh threshold. The 60s margin must act BEFORE
 * expiry (refresh a token about to lapse), never after — the original
 * implementation had the margin inverted, treating a token as valid until
 * 60s past its expiry, which handed out already-expired tokens.
 */
class OAuthTokenTest {

    @Test
    void shouldTreatAlreadyExpiredTokenAsExpired() {
        // Regression for the inverted margin: the old code returned false here
        // (token 30s past expiry was still considered valid).
        assertTrue(tokenExpiringAt(Instant.now().minusSeconds(30)).isExpired());
    }

    @Test
    void shouldTreatTokenExpiringWithinSafetyMarginAsExpired() {
        assertTrue(tokenExpiringAt(Instant.now().plusSeconds(30)).isExpired());
    }

    @Test
    void shouldTreatTokenBeyondSafetyMarginAsValid() {
        assertFalse(tokenExpiringAt(Instant.now().plusSeconds(120)).isExpired());
    }

    @Test
    void shouldTreatFreshStravaSixHourTokenAsValid() {
        assertFalse(tokenExpiringAt(Instant.now().plusSeconds(6 * 3600)).isExpired());
    }

    @Test
    void shouldTreatFreshSuuntoTwentyFourHourTokenAsValid() {
        assertFalse(tokenExpiringAt(Instant.now().plusSeconds(24 * 3600)).isExpired());
    }

    private OAuthToken tokenExpiringAt(Instant expiresAt) {
        OAuthToken token = new OAuthToken();
        token.setExpiresAt(expiresAt);
        return token;
    }
}
