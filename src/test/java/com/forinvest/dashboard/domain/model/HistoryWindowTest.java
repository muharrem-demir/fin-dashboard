package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HistoryWindowTest {

    private static PricePoint point(String date, String close) {
        return new PricePoint(LocalDate.parse(date), new BigDecimal(close));
    }

    @Test
    @DisplayName("keeps the most recent days when the provider returns more than the window")
    void keepsTheMostRecentDays() {
        List<PricePoint> points = List.of(
                point("2026-08-10", "100"),
                point("2026-08-11", "101"),
                point("2026-08-12", "102"),
                point("2026-08-13", "103"),
                point("2026-08-14", "104"));

        List<PricePoint> kept = HistoryWindow.ofDays(3).mostRecent(points);

        assertThat(kept)
                .extracting(PricePoint::date)
                .containsExactly(
                        LocalDate.parse("2026-08-12"), LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-14"));
    }

    @Test
    @DisplayName("orders oldest first whatever order the provider answered in")
    void ordersOldestFirst() {
        List<PricePoint> points =
                List.of(point("2026-08-14", "104"), point("2026-08-12", "102"), point("2026-08-13", "103"));

        List<PricePoint> kept = HistoryWindow.ofDays(5).mostRecent(points);

        assertThat(kept).isSortedAccordingTo(PricePoint::compareTo);
    }

    @Test
    @DisplayName("collapses a repeated day rather than letting it shorten the window")
    void collapsesRepeatedDays() {
        List<PricePoint> points =
                List.of(point("2026-08-12", "102"), point("2026-08-12", "102.5"), point("2026-08-13", "103"));

        List<PricePoint> kept = HistoryWindow.ofDays(2).mostRecent(points);

        assertThat(kept).hasSize(2);
        assertThat(kept.getFirst().close()).isEqualByComparingTo("102.5");
    }

    @Test
    @DisplayName("returns everything there is when the provider has fewer days than the window")
    void keepsShortHistoriesWhole() {
        List<PricePoint> points = List.of(point("2026-08-13", "103"), point("2026-08-14", "104"));

        assertThat(HistoryWindow.ofDays(5).mostRecent(points)).hasSize(2);
    }

    @Test
    @DisplayName("asks for enough calendar days to contain the trading days it wants")
    void spansMoreCalendarDaysThanTradingDays() {
        assertThat(HistoryWindow.ofDays(5).calendarSpanDays()).isGreaterThan(7);
        assertThat(HistoryWindow.ofDays(20).calendarSpanDays()).isGreaterThan(28);
    }

    @Test
    @DisplayName("rejects a window outside the supported range")
    void rejectsWindowsOutsideTheRange() {
        assertThatThrownBy(() -> HistoryWindow.ofDays(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between");
        assertThatThrownBy(() -> HistoryWindow.ofDays(HistoryWindow.MAX_DAYS + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null points")
    void rejectsNullPoints() {
        assertThatThrownBy(() -> HistoryWindow.ofDays(5).mostRecent(null)).isInstanceOf(NullPointerException.class);
    }
}
