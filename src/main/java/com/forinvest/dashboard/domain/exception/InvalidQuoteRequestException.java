package com.forinvest.dashboard.domain.exception;

/** Raised when a batch quote request asks for nothing, or for more tickers than are allowed. */
public final class InvalidQuoteRequestException extends DomainException {

    public InvalidQuoteRequestException(String message) {
        super(message);
    }

    public static InvalidQuoteRequestException empty() {
        return new InvalidQuoteRequestException("At least one ticker must be requested");
    }

    public static InvalidQuoteRequestException tooMany(int requested, int maximum) {
        return new InvalidQuoteRequestException(
                "At most %d tickers may be requested in one call but %d were given".formatted(maximum, requested));
    }
}
