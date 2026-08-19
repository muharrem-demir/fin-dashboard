package com.forinvest.dashboard.application.usecase;

import java.util.Objects;
import java.util.UUID;

import com.forinvest.dashboard.domain.exception.WatchlistEntryNotFoundException;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

/** Removes one entry from the watchlist. */
public final class RemoveWatchlistEntryUseCase {

    private final WatchlistRepository watchlistRepository;

    public RemoveWatchlistEntryUseCase(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = Objects.requireNonNull(watchlistRepository);
    }

    /**
     * Deletion is not silently idempotent, for the same reason removing a holding is not: the client
     * learns the id was wrong instead of assuming the delete took effect.
     *
     * @throws WatchlistEntryNotFoundException if no entry has this id
     */
    public void execute(UUID entryId) {
        Objects.requireNonNull(entryId, "entryId must not be null");
        if (!watchlistRepository.deleteById(entryId)) {
            throw new WatchlistEntryNotFoundException(entryId);
        }
    }
}
