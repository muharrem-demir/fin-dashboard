package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;

class QuoteSubscriptionTest {

    private static final SubscriberId SUBSCRIBER = SubscriberId.of("session-1");

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal(price), new BigDecimal("100"));
    }

    @Test
    @DisplayName("normalises symbols and collapses case-insensitive duplicates, keeping the client's order")
    void normalisesAndDeduplicates() {
        QuoteSubscription subscription = QuoteSubscription.of(SUBSCRIBER, List.of("msft", "AAPL", " aapl "));

        assertThat(subscription.tickers()).containsExactly(Ticker.of("MSFT"), Ticker.of("AAPL"));
    }

    @Test
    @DisplayName("rejects a subscription to nothing")
    void rejectsEmpty() {
        assertThatThrownBy(() -> QuoteSubscription.of(SUBSCRIBER, List.of()))
                .isInstanceOf(InvalidQuoteRequestException.class)
                .hasMessageContaining("At least one ticker");
    }

    @Test
    @DisplayName("rejects more symbols than one subscription may hold")
    void rejectsOversizedSubscription() {
        List<String> tooMany = IntStream.rangeClosed(1, QuoteSubscription.MAX_TICKERS + 1)
                .mapToObj("TICK%d"::formatted)
                .toList();

        assertThatThrownBy(() -> QuoteSubscription.of(SUBSCRIBER, tooMany))
                .isInstanceOf(InvalidQuoteRequestException.class)
                .hasMessageContaining("At most");
    }

    @Test
    @DisplayName("rejects a symbol that is not a ticker")
    void rejectsInvalidSymbol() {
        List<String> symbols = List.of("AAPL", "not a ticker");

        assertThatThrownBy(() -> QuoteSubscription.of(SUBSCRIBER, symbols)).isInstanceOf(InvalidTickerException.class);
    }

    @Test
    @DisplayName("takes only its own symbols out of a batch fetched for everybody, in its own order")
    void selectsItsOwnShareOfTheBatch() {
        QuoteSubscription subscription = QuoteSubscription.of(SUBSCRIBER, List.of("MSFT", "AAPL"));
        StockQuoteLookup batch = StockQuoteLookup.reconcile(
                List.of(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA")),
                List.of(quote("AAPL", "110"), quote("MSFT", "120"), quote("TSLA", "130")));

        StockQuoteLookup mine = subscription.select(batch);

        assertThat(mine.quotes()).extracting(StockQuote::ticker).containsExactly(Ticker.of("MSFT"), Ticker.of("AAPL"));
        assertThat(mine.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("reports its own symbols the provider had no data for, and nobody else's")
    void reportsItsOwnUnresolvedSymbols() {
        QuoteSubscription subscription = QuoteSubscription.of(SUBSCRIBER, List.of("AAPL", "NOSUCH"));
        StockQuoteLookup batch = StockQuoteLookup.reconcile(
                List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH"), Ticker.of("ALSOBAD")), List.of(quote("AAPL", "110")));

        StockQuoteLookup mine = subscription.select(batch);

        assertThat(mine.quotes()).extracting(StockQuote::ticker).containsExactly(Ticker.of("AAPL"));
        assertThat(mine.unresolved()).containsExactly(Ticker.of("NOSUCH"));
    }

    @Test
    @DisplayName("rejects a null subscriber, symbol list or batch")
    void rejectsNulls() {
        QuoteSubscription subscription = QuoteSubscription.of(SUBSCRIBER, List.of("AAPL"));

        assertThatThrownBy(() -> QuoteSubscription.of(null, List.of("AAPL"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> QuoteSubscription.of(SUBSCRIBER, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subscription.select(null)).isInstanceOf(NullPointerException.class);
    }
}
