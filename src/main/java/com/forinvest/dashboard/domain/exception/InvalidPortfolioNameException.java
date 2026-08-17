package com.forinvest.dashboard.domain.exception;

/** Raised when a portfolio name is blank or longer than the supported length. */
public final class InvalidPortfolioNameException extends DomainException {

    public InvalidPortfolioNameException(String message) {
        super(message);
    }
}
