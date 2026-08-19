package com.forinvest.dashboard.application.usecase;

import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

/** Lists every watched symbol. */
public final class ListWatchlistUseCase {

    private final WatchlistRepository watchlistRepository;

    public ListWatchlistUseCase(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = Objects.requireNonNull(watchlistRepository);
    }

    public List<WatchlistEntry> execute() {
        return watchlistRepository.findAll();
    }
}
