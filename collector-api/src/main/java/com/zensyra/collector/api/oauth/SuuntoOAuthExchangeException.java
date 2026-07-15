package com.zensyra.collector.api.oauth;

public class SuuntoOAuthExchangeException extends RuntimeException {
    public SuuntoOAuthExchangeException(String message) {
        super(message);
    }

    public SuuntoOAuthExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
