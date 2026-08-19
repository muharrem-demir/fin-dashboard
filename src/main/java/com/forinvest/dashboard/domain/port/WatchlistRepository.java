package com.forinvest.dashboard.domain.port;

import java.util.List;
import java.util.UUID;

import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;

/**
 * The domain's view of watchlist storage.
 *
 * <p>An outbound port, declared purely in domain types: {@code infrastructure.persistence} supplies
 * the adapter, and no JPA or Spring Data concept reaches inward.
 */
public interface WatchlistRepository {

    /** Every entry, oldest first, so a client sees the watchlist in the order it was built. */
    List<WatchlistEntry> findAll();

    boolean existsByTicker(Ticker ticker);

    /** Stores a new entry and returns the stored state. */
    WatchlistEntry save(WatchlistEntry entry);

    /** Deletes the entry. Returns {@code false} if no such entry existed. */
    boolean deleteById(UUID id);
}
