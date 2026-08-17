package com.forinvest.dashboard.application.usecase;

import java.util.Objects;
import java.util.UUID;

import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Loads a single portfolio with its holdings. */
public final class GetPortfolioUseCase {

    private final PortfolioRepository portfolioRepository;

    public GetPortfolioUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
    }

    /**
     * @throws PortfolioNotFoundException if no portfolio has this id
     */
    public Portfolio execute(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        return portfolioRepository.findById(portfolioId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }
}
