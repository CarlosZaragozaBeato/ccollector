package com.zensyra.collector.core.oauth;

import java.time.Instant;

public record TokenRefreshResult(
        String accessToken,
        String refreshToken,
        Instant expiresAt
){ }
