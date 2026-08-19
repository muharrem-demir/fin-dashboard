package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockQuoteSnapshotTest {

    private static final HistoryWindow WINDOW = HistoryWindow.ofDays(5);

    private static StockQuote quote(String ticker) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal("100"), new BigDecimal("99"));
    }

    private static PriceHistory history(String ticker) {
        return PriceHistory.of(
                Ticker.of(ticker),
                List.of(new PricePoint(LocalDate.parse("2026-08-14"), new BigDecimal("100"))),
                WINDOW);
    }

    private static StockQuoteLookup lookup(List<String> requested, List<String> quoted) {
        return StockQuoteLookup.reconcile(
                requested.stream().map(Ticker::of).toList(),
                quoted.stream().map(StockQuoteSnapshotTest::quote).toList());
    }

    @Test
    @DisplayName("says history was not included when it was never asked for")
    void reportsHistoryWasNotAskedFor() {
        StockQuoteSnapshot snapshot = StockQuoteSnapshot.withoutHistory(lookup(List.of("AAPL"), List.of("AAPL")));

        assertThat(snapshot.historyIncluded()).isFalse();
        assertThat(snapshot.histories()).isEmpty();
    }

    @Test
    @DisplayName("says history was included even when the provider returned none")
    void distinguishesAskedFromFound() {
        StockQuoteSnapshot snapshot = StockQuoteSnapshot.of(lookup(List.of("AAPL"), List.of("AAPL")), List.of());

        assertThat(snapshot.historyIncluded()).isTrue();
        assertThat(snapshot.histories()).isEmpty();
    }

    @Test
    @DisplayName("orders histories the way the lookup orders its tickers")
    void ordersHistoriesLikeTheLookup() {
        StockQuoteLookup quotes = lookup(List.of("AAPL", "MSFT", "TSLA"), List.of("AAPL", "MSFT", "TSLA"));

        StockQuoteSnapshot snapshot =
                StockQuoteSnapshot.of(quotes, List.of(history("TSLA"), history("AAPL"), history("MSFT")));

        assertThat(snapshot.histories())
                .extracting(PriceHistory::ticker)
                .containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA"));
    }

    @Test
    @DisplayName("keeps history for a ticker that had no quote, since the two providers answer separately")
    void keepsHistoryForUnquotedTickers() {
        StockQuoteLookup quotes = lookup(List.of("AAPL", "NEWCO"), List.of("AAPL"));

        StockQuoteSnapshot snapshot = StockQuoteSnapshot.of(quotes, List.of(history("NEWCO"), history("AAPL")));

        assertThat(snapshot.histories())
                .extracting(PriceHistory::ticker)
                .containsExactly(Ticker.of("AAPL"), Ticker.of("NEWCO"));
    }

    @Test
    @DisplayName("drops an empty history rather than reporting a ticker with no points")
    void dropsEmptyHistories() {
        StockQuoteLookup quotes = lookup(List.of("AAPL", "NOSUCH"), List.of("AAPL"));

        StockQuoteSnapshot snapshot = StockQuoteSnapshot.of(
                quotes, List.of(history("AAPL"), PriceHistory.of(Ticker.of("NOSUCH"), List.of(), WINDOW)));

        assertThat(snapshot.histories()).extracting(PriceHistory::ticker).containsExactly(Ticker.of("AAPL"));
    }

    @Test
    @DisplayName("ignores a history for a ticker nobody asked about")
    void ignoresUnrequestedHistories() {
        StockQuoteSnapshot snapshot =
                StockQuoteSnapshot.of(lookup(List.of("AAPL"), List.of("AAPL")), List.of(history("MSFT")));

        assertThat(snapshot.histories()).extracting(PriceHistory::ticker).containsExactly();
    }

    @Test
    @DisplayName("refuses to carry histories while claiming none were asked for")
    void refusesInconsistentSnapshots() {
        StockQuoteLookup quotes = lookup(List.of("AAPL"), List.of("AAPL"));
        List<PriceHistory> histories = List.of(history("AAPL"));

        assertThatThrownBy(() -> new StockQuoteSnapshot(quotes, histories, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockQuoteSnapshot.of(null, histories)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StockQuoteSnapshot.of(quotes, null)).isInstanceOf(NullPointerException.class);
    }
}
