package com.forinvest.dashboard.infrastructure.web;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.infrastructure.web.dto.WatchlistEntryResponse;

/**
 * Turns watchlist entries into the API's response shape.
 *
 * <p>Separate from the domain so the wire format can gain a field without a business rule moving.
 */
@Component
class WatchlistWebMapper {

    WatchlistEntryResponse toResponse(WatchlistEntry entry) {
        return new WatchlistEntryResponse(entry.id(), entry.ticker().symbol());
    }
}
