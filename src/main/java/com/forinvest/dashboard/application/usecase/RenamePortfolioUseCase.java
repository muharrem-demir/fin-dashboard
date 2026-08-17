package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.RenamePortfolioCommand;
import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Renames an existing portfolio, keeping its identity and holdings. */
public final class RenamePortfolioUseCase {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRunner transactionRunner;

    public RenamePortfolioUseCase(PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
        this.transactionRunner = Objects.requireNonNull(transactionRunner);
    }

    /**
     * @throws PortfolioNotFoundException if no portfolio has this id
     */
    public Portfolio execute(RenamePortfolioCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return transactionRunner.inTransaction(() -> {
            Portfolio portfolio = portfolioRepository
                    .findById(command.portfolioId())
                    .orElseThrow(() -> new PortfolioNotFoundException(command.portfolioId()));
            return portfolioRepository.save(portfolio.rename(command.name()));
        });
    }
}
