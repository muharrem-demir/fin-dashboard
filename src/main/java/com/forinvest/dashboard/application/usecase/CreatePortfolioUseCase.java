package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.CreatePortfolioCommand;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Creates a new, empty portfolio. */
public final class CreatePortfolioUseCase {

    private final PortfolioRepository portfolioRepository;

    public CreatePortfolioUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
    }

    public Portfolio execute(CreatePortfolioCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return portfolioRepository.save(Portfolio.create(command.name()));
    }
}
