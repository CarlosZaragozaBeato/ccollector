package com.zensyra.collector.api.oauth;

import java.time.Instant;

public record StravaOAuthToken(
        String athleteId,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String scope
) {
}
