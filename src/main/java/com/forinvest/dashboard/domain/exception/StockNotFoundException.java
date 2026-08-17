package com.forinvest.dashboard.domain.exception;

import java.util.UUID;

import com.forinvest.dashboard.domain.model.Ticker;

/**
 * Raised when a stock is removed from a portfolio that does not hold it.
 *
 * <p>Removal is deliberately not idempotent: reporting "no such holding" is more useful to a
 * dashboard client than silently succeeding on a ticker the user mistyped.
 */
public final class StockNotFoundException extends DomainException {

    private final UUID portfolioId;
    private final Ticker ticker;

    public StockNotFoundException(UUID portfolioId, Ticker ticker) {
        super("Portfolio %s does not hold ticker %s".formatted(portfolioId, ticker));
        this.portfolioId = portfolioId;
        this.ticker = ticker;
    }

    public UUID portfolioId() {
        return portfolioId;
    }

    public Ticker ticker() {
        return ticker;
    }
}
