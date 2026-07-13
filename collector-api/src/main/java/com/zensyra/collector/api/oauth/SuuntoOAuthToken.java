package com.zensyra.collector.api.oauth;

import java.time.Instant;

public record SuuntoOAuthToken(
        String user,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String scope
) {
}
