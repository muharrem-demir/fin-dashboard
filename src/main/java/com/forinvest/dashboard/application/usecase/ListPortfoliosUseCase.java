package com.forinvest.dashboard.application.usecase;

import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Lists every portfolio. */
public final class ListPortfoliosUseCase {

    private final PortfolioRepository portfolioRepository;

    public ListPortfoliosUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
    }

    public List<Portfolio> execute() {
        return portfolioRepository.findAll();
    }
}
