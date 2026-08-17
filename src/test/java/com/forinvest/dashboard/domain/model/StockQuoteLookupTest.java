package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockQuoteLookupTest {

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal(price), new BigDecimal("100"));
    }

    @Test
    @DisplayName("returns quotes in the order they were requested, not the order they arrived")
    void preservesRequestedOrder() {
        List<Ticker> requested = List.of(Ticker.of("MSFT"), Ticker.of("AAPL"), Ticker.of("TSLA"));
        List<StockQuote> found = List.of(quote("AAPL", "110"), quote("TSLA", "120"), quote("MSFT", "130"));

        StockQuoteLookup lookup = StockQuoteLookup.reconcile(requested, found);

        assertThat(lookup.quotes()).extracting(q -> q.ticker().symbol()).containsExactly("MSFT", "AAPL", "TSLA");
        assertThat(lookup.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("reports requested tickers the provider did not quote")
    void reportsUnresolvedTickers() {
        List<Ticker> requested = List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH"), Ticker.of("MSFT"));
        List<StockQuote> found = List.of(quote("AAPL", "110"), quote("MSFT", "130"));

        StockQuoteLookup lookup = StockQuoteLookup.reconcile(requested, found);

        assertThat(lookup.quotes()).hasSize(2);
        assertThat(lookup.unresolved()).containsExactly(Ticker.of("NOSUCH"));
    }

    @Test
    @DisplayName("treats every ticker as unresolved when the provider returns nothing")
    void allUnresolvedWhenNothingFound() {
        List<Ticker> requested = List.of(Ticker.of("AAPL"), Ticker.of("MSFT"));

        StockQuoteLookup lookup = StockQuoteLookup.reconcile(requested, List.of());

        assertThat(lookup.quotes()).isEmpty();
        assertThat(lookup.isEmpty()).isTrue();
        assertThat(lookup.unresolved()).containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));
    }

    @Test
    @DisplayName("ignores quotes for tickers that were never requested")
    void ignoresUnrequestedQuotes() {
        StockQuoteLookup lookup = StockQuoteLookup.reconcile(
                List.of(Ticker.of("AAPL")), List.of(quote("AAPL", "110"), quote("TSLA", "9")));

        assertThat(lookup.quotes()).extracting(q -> q.ticker().symbol()).containsExactly("AAPL");
        assertThat(lookup.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("holds unmodifiable lists")
    void isImmutable() {
        StockQuoteLookup lookup = StockQuoteLookup.reconcile(List.of(Ticker.of("AAPL")), List.of(quote("AAPL", "110")));

        assertThat(lookup.quotes()).isUnmodifiable();
        assertThat(lookup.unresolved()).isUnmodifiable();
    }
}
