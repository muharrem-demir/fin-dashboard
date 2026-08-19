package com.forinvest.dashboard.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How much price history to report: a number of trading days.
 *
 * <p>Days here are <em>trading</em> days, not calendar days. A five-day window asked for on a
 * Monday still answers with five closes; counting calendar days would answer with two and look
 * broken every weekend and every public holiday. That is a decision about what "five days of
 * history" means to a reader of the dashboard, which is why it lives in the domain and not in
 * whichever provider happens to be answering.
 *
 * <p>A provider cannot be asked for "the last five trading days" directly — it is asked for a span
 * of calendar days and answers with the trading days inside it. {@link #calendarSpanDays()} sizes
 * that span generously and {@link #mostRecent(List)} cuts the answer back down, so a run of market
 * holidays costs an oversized request rather than a short history.
 */
public record HistoryWindow(int days) {

    /** Fewer than one day is not a window; a request for it is a configuration mistake. */
    public static final int MIN_DAYS = 1;

    /**
     * Upper bound. Every window is fetched per ticker on demand, so an unbounded one would let a
     * single request ask the provider for decades of data on fifty symbols.
     */
    public static final int MAX_DAYS = 365;

    /**
     * Weekends alone cost two calendar days in seven; the rest is slack for public holidays and
     * exchange closures, which cluster (a Christmas week can close four days in a row).
     */
    private static final int CALENDAR_DAYS_PER_TRADING_DAY = 2;

    private static final int HOLIDAY_SLACK_DAYS = 10;

    public HistoryWindow {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "History window must be between %d and %d days but was %d".formatted(MIN_DAYS, MAX_DAYS, days));
        }
    }

    public static HistoryWindow ofDays(int days) {
        return new HistoryWindow(days);
    }

    /** The calendar span to ask a provider for, sized to contain at least {@link #days()} of trading. */
    public int calendarSpanDays() {
        return days * CALENDAR_DAYS_PER_TRADING_DAY + HOLIDAY_SLACK_DAYS;
    }

    /**
     * The window's worth of the most recent points, oldest first.
     *
     * <p>Sorts and de-duplicates by date — a provider is free to answer in any order, and repeating
     * a day would silently shorten the window by one real day.
     */
    public List<PricePoint> mostRecent(List<PricePoint> points) {
        Objects.requireNonNull(points, "points must not be null");

        Map<LocalDate, PricePoint> byDate = new LinkedHashMap<>();
        for (PricePoint point : points) {
            byDate.put(point.date(), point);
        }

        List<PricePoint> ordered = new ArrayList<>(byDate.values());
        ordered.sort(Comparator.naturalOrder());

        int from = Math.max(0, ordered.size() - days);
        return List.copyOf(ordered.subList(from, ordered.size()));
    }
}
