package com.forinvest.dashboard.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A ticker's recent closes, oldest first, together with the window they were cut to.
 *
 * <p>Built through {@link #of(Ticker, List, HistoryWindow)} so that the window decides what the
 * history contains: a provider that answers with too many days, too few, or out of order still
 * produces the same shape here. Nothing is fabricated to fill a short history — a symbol that has
 * only traded for two days has a two-point history, and saying so is more useful than padding it.
 *
 * <p>The window travels with the points because it is the only thing that explains them. Three
 * points against a five-day window says "the provider had no more"; three against a three-day
 * window says the history is complete. A reader given the points alone cannot tell those apart.
 */
public record PriceHistory(Ticker ticker, HistoryWindow window, List<PricePoint> points) {

    public PriceHistory {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(points, "points must not be null");
        points = List.copyOf(points);
    }

    /** Trims and orders {@code points} to what {@code window} asks for. */
    public static PriceHistory of(Ticker ticker, List<PricePoint> points, HistoryWindow window) {
        Objects.requireNonNull(window, "window must not be null");
        return new PriceHistory(ticker, window, window.mostRecent(points));
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
