package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.sync.IntegrationSource;

import java.io.IOException;

public interface TokenRefresher {
    IntegrationSource source();
    TokenRefreshResult refresh(String refreshToken) throws IOException, InterruptedException;
}
