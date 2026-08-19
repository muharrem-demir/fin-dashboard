package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceHistoryTest {

    private static PricePoint point(String date, String close) {
        return new PricePoint(LocalDate.parse(date), new BigDecimal(close));
    }

    @Test
    @DisplayName("cuts the provider's answer down to the window and keeps the window with it")
    void appliesTheWindow() {
        PriceHistory history = PriceHistory.of(
                Ticker.of("AAPL"),
                List.of(point("2026-08-12", "102"), point("2026-08-13", "103"), point("2026-08-14", "104")),
                HistoryWindow.ofDays(2));

        assertThat(history.points()).hasSize(2);
        assertThat(history.points().getFirst().date()).isEqualTo(LocalDate.parse("2026-08-13"));
        assertThat(history.window().days()).isEqualTo(2);
        assertThat(history.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("does not pad a history the provider had less data for")
    void doesNotPadShortHistories() {
        PriceHistory history =
                PriceHistory.of(Ticker.of("NEWCO"), List.of(point("2026-08-14", "12")), HistoryWindow.ofDays(5));

        assertThat(history.points()).hasSize(1);
        assertThat(history.window().days()).isEqualTo(5);
    }

    @Test
    @DisplayName("is empty when the provider had no data at all")
    void isEmptyWithoutPoints() {
        assertThat(PriceHistory.of(Ticker.of("NOSUCH"), List.of(), HistoryWindow.ofDays(5))
                        .isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("cannot be built without a ticker, a window or points")
    void rejectsMissingParts() {
        List<PricePoint> points = List.of(point("2026-08-14", "12"));
        HistoryWindow window = HistoryWindow.ofDays(5);

        assertThatThrownBy(() -> PriceHistory.of(null, points, window)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PriceHistory.of(Ticker.of("AAPL"), points, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PriceHistory.of(Ticker.of("AAPL"), null, window))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects a point with no date, no close, or a negative close")
    void rejectsInvalidPoints() {
        assertThatThrownBy(() -> new PricePoint(null, BigDecimal.ONE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PricePoint(LocalDate.parse("2026-08-14"), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PricePoint(LocalDate.parse("2026-08-14"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
