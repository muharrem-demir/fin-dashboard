package com.forinvest.dashboard.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.forinvest.dashboard.domain.exception.InvalidPortfolioNameException;
import com.forinvest.dashboard.domain.exception.InvalidShareCountException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;

/**
 * A named collection of stock holdings, and the aggregate root of this domain.
 *
 * <p>The type is immutable: every operation returns a new {@code Portfolio} rather than mutating
 * this one, so a half-applied change can never be observed and instances are safe to share.
 *
 * <p>All portfolio rules live here rather than in a service, which is what lets them be tested
 * without a database, a Spring context or a single mock.
 */
public record Portfolio(UUID id, String name, List<Holding> holdings) {

    public static final int MAX_NAME_LENGTH = 100;

    public Portfolio {
        Objects.requireNonNull(id, "id must not be null");
        name = validateName(name);
        Objects.requireNonNull(holdings, "holdings must not be null");
        holdings = List.copyOf(holdings);
        requireUniqueTickers(holdings);
    }

    /** Creates a brand-new, empty portfolio with a freshly generated identity. */
    public static Portfolio create(String name) {
        return new Portfolio(UUID.randomUUID(), name, List.of());
    }

    /**
     * Adds shares of a ticker to this portfolio.
     *
     * <p>This is the core business rule: if the ticker is already held, the resulting position is
     * the <em>sum</em> of the existing and the added shares; otherwise a new holding is appended.
     * Because {@link Ticker} normalises case, adding {@code aapl} merges into an existing
     * {@code AAPL} position rather than creating a second one.
     *
     * <p>Existing holdings keep their relative order, and a merged holding stays in place, so the
     * dashboard does not reshuffle when a user tops up a position.
     */
    public Portfolio addStock(Ticker ticker, int shares) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        if (shares <= 0) {
            throw InvalidShareCountException.notPositive(shares);
        }

        List<Holding> updated = new ArrayList<>(holdings.size() + 1);
        boolean merged = false;
        for (Holding holding : holdings) {
            if (holding.ticker().equals(ticker)) {
                updated.add(holding.plusShares(shares));
                merged = true;
            } else {
                updated.add(holding);
            }
        }
        if (!merged) {
            updated.add(new Holding(ticker, shares));
        }
        return new Portfolio(id, name, updated);
    }

    /**
     * Removes the whole position in a ticker.
     *
     * @throws StockNotFoundException if this portfolio does not hold the ticker
     */
    public Portfolio removeStock(Ticker ticker) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        List<Holding> remaining = holdings.stream()
                .filter(holding -> !holding.ticker().equals(ticker))
                .toList();
        if (remaining.size() == holdings.size()) {
            throw new StockNotFoundException(id, ticker);
        }
        return new Portfolio(id, name, remaining);
    }

    /** Returns this portfolio under a new name, keeping its identity and holdings. */
    public Portfolio rename(String newName) {
        return new Portfolio(id, newName, holdings);
    }

    public Optional<Holding> findHolding(Ticker ticker) {
        return holdings.stream()
                .filter(holding -> holding.ticker().equals(ticker))
                .findFirst();
    }

    public boolean holds(Ticker ticker) {
        return findHolding(ticker).isPresent();
    }

    public int holdingCount() {
        return holdings.size();
    }

    /** Total number of shares across every position, useful as a dashboard summary figure. */
    public long totalShares() {
        return holdings.stream().mapToLong(Holding::shares).sum();
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new InvalidPortfolioNameException("Portfolio name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidPortfolioNameException("Portfolio name must be at most %d characters but was %d"
                    .formatted(MAX_NAME_LENGTH, trimmed.length()));
        }
        return trimmed;
    }

    private static void requireUniqueTickers(List<Holding> holdings) {
        long distinct = holdings.stream().map(Holding::ticker).distinct().count();
        if (distinct != holdings.size()) {
            throw new IllegalArgumentException("A portfolio must not hold the same ticker twice");
        }
    }
}
