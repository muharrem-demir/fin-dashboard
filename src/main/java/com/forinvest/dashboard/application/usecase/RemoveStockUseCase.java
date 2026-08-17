package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.RemoveStockCommand;
import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** Removes the whole position in a ticker from a portfolio. */
public final class RemoveStockUseCase {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRunner transactionRunner;

    public RemoveStockUseCase(PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        this.portfolioRepository = Objects.requireNonNull(portfolioRepository);
        this.transactionRunner = Objects.requireNonNull(transactionRunner);
    }

    /**
     * @throws PortfolioNotFoundException if no portfolio has this id
     * @throws StockNotFoundException if the portfolio does not hold the ticker
     */
    public Portfolio execute(RemoveStockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Ticker ticker = Ticker.of(command.ticker());
        return transactionRunner.inTransaction(() -> {
            Portfolio portfolio = portfolioRepository
                    .findById(command.portfolioId())
                    .orElseThrow(() -> new PortfolioNotFoundException(command.portfolioId()));
            return portfolioRepository.save(portfolio.removeStock(ticker));
        });
    }
}
