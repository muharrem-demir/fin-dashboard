package com.forinvest.dashboard.domain.exception;

import java.util.UUID;

/** Raised when a watchlist entry is referenced by an id that does not exist. */
public final class WatchlistEntryNotFoundException extends DomainException {

    private final UUID entryId;

    public WatchlistEntryNotFoundException(UUID entryId) {
        super("Watchlist entry %s was not found".formatted(entryId));
        this.entryId = entryId;
    }

    public UUID entryId() {
        return entryId;
    }
}
