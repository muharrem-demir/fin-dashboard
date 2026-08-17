package com.forinvest.dashboard.domain.exception;

import java.util.UUID;

/** Raised when a portfolio is referenced by an id that does not exist. */
public final class PortfolioNotFoundException extends DomainException {

    private final UUID portfolioId;

    public PortfolioNotFoundException(UUID portfolioId) {
        super("Portfolio %s was not found".formatted(portfolioId));
        this.portfolioId = portfolioId;
    }

    public UUID portfolioId() {
        return portfolioId;
    }
}
