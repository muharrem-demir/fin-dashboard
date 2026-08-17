package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.AddStockCommand;
import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/**
 * Adds shares of a ticker to a portfolio.
 *
 * <p>Whether this creates a new holding or tops up an existing one is decided by
 * {@link Portfolio#addStock}, not here — this use case only sequences load, apply and save. The
 * whole sequence runs in one transaction so two concurrent additions cannot read the same starting
 * position and lose one of the two updates.
 */
public final class AddStockUseCase {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRunner transactionRunner;

    public AddStockUseCase(PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
        this.transactionRunner = Objects.requireNonNull(transactionRunner);
    }

    /**
     * @throws PortfolioNotFoundException if no portfolio has this id
     */
    public Portfolio execute(AddStockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Ticker ticker = Ticker.of(command.ticker());
        return transactionRunner.inTransaction(() -> {
            Portfolio portfolio = portfolioRepository
                    .findById(command.portfolioId())
                    .orElseThrow(() -> new PortfolioNotFoundException(command.portfolioId()));
            return portfolioRepository.save(portfolio.addStock(ticker, command.shares()));
        });
    }
}
