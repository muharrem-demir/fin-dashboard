package com.forinvest.dashboard.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;

/**
 * JPA mapping of the watchlist table.
 *
 * <p>Anaemic on purpose, like every entity here: it carries no rules and never leaves this package.
 *
 * <p>{@code createdAt} exists only at this level. Nothing in the domain decides anything from it, so
 * giving it to {@link com.forinvest.dashboard.domain.model.WatchlistEntry} would be handing the
 * model a field it has no use for; the database stamps it and the ordering of a listing is the one
 * thing it is read for.
 */
@Entity
@Table(
        name = "watchlist",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_watchlist_ticker",
                        columnNames = {"ticker"}))
class WatchlistEntryEntity {

    @Id
    private UUID id;

    @Column(name = "ticker", nullable = false, length = 16)
    private String ticker;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WatchlistEntryEntity() {
        // Required by JPA.
    }

    WatchlistEntryEntity(UUID id, String ticker) {
        this.id = id;
        this.ticker = ticker;
    }

    UUID getId() {
        return id;
    }

    String getTicker() {
        return ticker;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
