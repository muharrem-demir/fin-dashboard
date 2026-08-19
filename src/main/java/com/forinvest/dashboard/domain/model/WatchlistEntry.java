package com.forinvest.dashboard.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * One symbol a user is watching, independently of any portfolio.
 *
 * <p>A watchlist entry is its own little aggregate: it has an identity, a ticker, and no state to
 * change. The type it carries is {@link Ticker}, not a {@code String}, which is what makes the
 * upper-casing rule apply here for free — {@code aapl} and {@code AAPL} are the same entry, and the
 * database can never hold a symbol in a shape the domain would not recognise.
 *
 * <p>When the entry was added is a storage detail, exactly as it is for a portfolio: nothing in the
 * domain decides anything from it, so it lives on the entity and not here.
 */
public record WatchlistEntry(UUID id, Ticker ticker) {

    public WatchlistEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ticker, "ticker must not be null");
    }

    /**
     * Creates a brand-new entry with a freshly generated identity.
     *
     * <p>The symbol is normalised by {@link Ticker}, so what reaches storage is always upper case
     * however the caller typed it.
     */
    public static WatchlistEntry watch(String symbol) {
        return new WatchlistEntry(UUID.randomUUID(), Ticker.of(symbol));
    }
}
