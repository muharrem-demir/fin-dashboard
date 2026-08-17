package com.forinvest.dashboard.application.usecase;

import java.util.Objects;
import java.util.UUID;

import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Deletes a portfolio together with its holdings. */
public final class DeletePortfolioUseCase {

    private final PortfolioRepository portfolioRepository;

    public DeletePortfolioUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
    }

    /**
     * Deletion is not silently idempotent: deleting an unknown portfolio is reported so the client
     * learns the id was wrong instead of assuming the delete took effect.
     *
     * @throws PortfolioNotFoundException if no portfolio has this id
     */
    public void execute(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        if (!portfolioRepository.deleteById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }
    }
}
