package com.forinvest.dashboard.domain.exception;

/**
 * Root of every error the domain can raise.
 *
 * <p>Sealed so the web layer's exception handler can map the complete set of domain failures to HTTP
 * responses and the compiler will flag a new subtype that nobody has mapped yet.
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidPortfolioNameException,
                InvalidQuoteRequestException,
                InvalidShareCountException,
                InvalidTickerException,
                PortfolioNotFoundException,
                StockNotFoundException,
                StockQuoteUnavailableException,
                TickerAlreadyWatchedException,
                WatchlistEntryNotFoundException {

    protected DomainException(String message) {
        super(message);
    }
}
