package com.forinvest.dashboard.domain.exception;

import com.forinvest.dashboard.domain.model.Ticker;

/**
 * Raised when a ticker already on the watchlist is added again.
 *
 * <p>Adding is deliberately not idempotent here, and deliberately not additive the way
 * {@code Portfolio.addStock} is: a watchlist entry carries no quantity, so a second add has nothing
 * to merge and can only be a mistake worth reporting.
 */
public final class TickerAlreadyWatchedException extends DomainException {

    private final Ticker ticker;

    public TickerAlreadyWatchedException(Ticker ticker) {
        super("Ticker %s is already on the watchlist".formatted(ticker));
        this.ticker = ticker;
    }

    public Ticker ticker() {
        return ticker;
    }
}
