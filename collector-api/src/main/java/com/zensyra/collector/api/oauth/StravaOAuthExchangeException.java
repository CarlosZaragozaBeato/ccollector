package com.zensyra.collector.api.oauth;

public class StravaOAuthExchangeException extends RuntimeException {
    public StravaOAuthExchangeException(String message) {
        super(message);
    }

    public StravaOAuthExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
