package com.forinvest.dashboard.domain.exception;

/**
 * Raised when market data cannot be obtained from the quote provider.
 *
 * <p>This is an upstream failure, not a client mistake, so it maps to 502 rather than 4xx or 500 —
 * the request was fine; the dependency was not. The provider's own exception is kept as the cause
 * for the server log and never shown to the caller.
 */
public final class StockQuoteUnavailableException extends DomainException {

    public StockQuoteUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }

    public StockQuoteUnavailableException(String message) {
        super(message);
    }
}
