package com.forinvest.dashboard.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;

/**
 * Translates between the watchlist entity and the domain record.
 *
 * <p>Reading the ticker back through {@link Ticker#of} rather than trusting the column is what keeps
 * a row written by anything other than this application — a migration, a manual fix — from becoming
 * a domain object the rest of the system believes is normalised.
 */
@Component
class WatchlistPersistenceMapper {

    WatchlistEntry toDomain(WatchlistEntryEntity entity) {
        return new WatchlistEntry(entity.getId(), Ticker.of(entity.getTicker()));
    }

    WatchlistEntryEntity toEntity(WatchlistEntry entry) {
        return new WatchlistEntryEntity(entry.id(), entry.ticker().symbol());
    }
}
