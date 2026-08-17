package com.forinvest.dashboard.domain.exception;

/** Raised when a string cannot be interpreted as a stock ticker symbol. */
public final class InvalidTickerException extends DomainException {

    public InvalidTickerException(String message) {
        super(message);
    }
}
