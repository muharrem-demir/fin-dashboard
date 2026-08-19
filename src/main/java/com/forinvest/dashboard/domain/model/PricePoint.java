package com.forinvest.dashboard.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of a ticker's price history: the day, and what it closed at.
 *
 * <p>Only the close is carried. A dashboard plots a line, and the close is the figure that line is
 * made of; open, high, low and volume would be data nobody asked for on every quote request.
 *
 * <p>The date is the trading day in the exchange's own timezone, not ours — a Tokyo close belongs
 * to the Tokyo day it happened on, whatever date it was in UTC at the time.
 */
public record PricePoint(LocalDate date, BigDecimal close) implements Comparable<PricePoint> {

    public PricePoint {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(close, "close must not be null");
        if (close.signum() < 0) {
            throw new IllegalArgumentException("Close price must not be negative but was " + close);
        }
    }

    /** Oldest first, which is the order a history is read and plotted in. */
    @Override
    public int compareTo(PricePoint other) {
        return date.compareTo(other.date);
    }
}
