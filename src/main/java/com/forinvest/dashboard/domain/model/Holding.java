package com.forinvest.dashboard.domain.model;

import java.util.Objects;

import com.forinvest.dashboard.domain.exception.InvalidShareCountException;

/**
 * A position in a single stock: how many shares of one ticker a portfolio holds.
 *
 * <p>A holding always represents a real position, so the share count is strictly positive. A
 * position that reaches zero is removed from the portfolio rather than kept at zero.
 */
public record Holding(Ticker ticker, int shares) {

    public Holding {
        Objects.requireNonNull(ticker, "ticker must not be null");
        if (shares <= 0) {
            throw InvalidShareCountException.notPositive(shares);
        }
    }

    public static Holding of(String ticker, int shares) {
        return new Holding(Ticker.of(ticker), shares);
    }

    /**
     * Returns this holding with {@code additional} shares added.
     *
     * <p>This is the arithmetic behind the additive-upsert rule. Overflow is reported as a domain
     * error rather than wrapping silently into a negative position.
     */
    public Holding plusShares(int additional) {
        if (additional <= 0) {
            throw InvalidShareCountException.notPositive(additional);
        }
        try {
            return new Holding(ticker, Math.addExact(shares, additional));
        } catch (ArithmeticException overflow) {
            throw InvalidShareCountException.overflow(shares, additional);
        }
    }
}
