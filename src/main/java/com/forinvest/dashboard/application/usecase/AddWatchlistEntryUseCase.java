package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.AddWatchlistEntryCommand;
import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.domain.exception.TickerAlreadyWatchedException;
import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

/**
 * Adds one symbol to the watchlist, refusing a duplicate.
 *
 * <p>The check and the insert run inside one transaction because they are a check-then-act: two
 * concurrent requests for the same symbol would otherwise both see an empty watchlist. The unique
 * constraint on the table is the safety net behind that, not the implementation of it.
 */
public final class AddWatchlistEntryUseCase {

    private final WatchlistRepository watchlistRepository;
    private final TransactionRunner transactionRunner;

    public AddWatchlistEntryUseCase(WatchlistRepository watchlistRepository, TransactionRunner transactionRunner) {
        this.watchlistRepository = Objects.requireNonNull(watchlistRepository);
        this.transactionRunner = Objects.requireNonNull(transactionRunner);
    }

    /**
     * @throws TickerAlreadyWatchedException if the symbol is already watched, in any case
     */
    public WatchlistEntry execute(AddWatchlistEntryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        WatchlistEntry entry = WatchlistEntry.watch(command.ticker());
        return transactionRunner.inTransaction(() -> {
            if (watchlistRepository.existsByTicker(entry.ticker())) {
                throw new TickerAlreadyWatchedException(entry.ticker());
            }
            return watchlistRepository.save(entry);
        });
    }
}
