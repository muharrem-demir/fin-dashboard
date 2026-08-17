package com.forinvest.dashboard.infrastructure.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.Ticker;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

/**
 * The Yahoo adapter, with the library's static entry point stubbed.
 *
 * <p>Deliberately never touches the network: Yahoo's availability must not decide whether this
 * build passes. What is verified here is our side of the contract — that a batch becomes exactly
 * one upstream call, that missing data is dropped rather than invented, and that every upstream
 * failure is translated into a domain error.
 */
class YahooFinanceStockQuoteAdapterTest {

    private final YahooFinanceStockQuoteAdapter adapter =
            new YahooFinanceStockQuoteAdapter(new YahooFinanceProperties(5000));

    private static Stock stock(String symbol, String price, String previousClose) {
        Stock stock = new Stock(symbol);
        yahoofinance.quotes.stock.StockQuote quote = new yahoofinance.quotes.stock.StockQuote(symbol);
        quote.setPrice(price == null ? null : new BigDecimal(price));
        quote.setPreviousClose(previousClose == null ? null : new BigDecimal(previousClose));
        stock.setQuote(quote);
        return stock;
    }

    @Test
    @DisplayName("fetches every ticker in one upstream call")
    void batchesIntoASingleCall() {
        Map<String, Stock> upstream = new LinkedHashMap<>();
        upstream.put("AAPL", stock("AAPL", "150.25", "148.50"));
        upstream.put("MSFT", stock("MSFT", "198.00", "200.00"));

        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            ArgumentCaptor<String[]> symbols = ArgumentCaptor.forClass(String[].class);
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class))).thenReturn(upstream);

            var quotes = adapter.findQuotes(List.of(Ticker.of("AAPL"), Ticker.of("MSFT")));

            yahoo.verify(() -> YahooFinance.get(symbols.capture()), Mockito.times(1));
            assertThat(symbols.getValue()).containsExactly("AAPL", "MSFT");
            assertThat(quotes).hasSize(2);
            assertThat(quotes.getFirst().ticker()).isEqualTo(Ticker.of("AAPL"));
            assertThat(quotes.getFirst().price()).isEqualByComparingTo("150.25");
            assertThat(quotes.getFirst().percentChange()).contains(new BigDecimal("1.18"));
        }
    }

    @Test
    @DisplayName("omits a ticker the provider did not return")
    void omitsMissingSymbols() {
        Map<String, Stock> upstream = Map.of("AAPL", stock("AAPL", "150.00", "150.00"));

        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class))).thenReturn(upstream);

            var quotes = adapter.findQuotes(List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH")));

            assertThat(quotes).hasSize(1);
            assertThat(quotes.getFirst().ticker()).isEqualTo(Ticker.of("AAPL"));
        }
    }

    @Test
    @DisplayName("omits a ticker quoted without a price rather than inventing zero")
    void omitsPricelessQuotes() {
        Map<String, Stock> upstream = Map.of("AAPL", stock("AAPL", null, "150.00"));

        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class))).thenReturn(upstream);

            assertThat(adapter.findQuotes(List.of(Ticker.of("AAPL")))).isEmpty();
        }
    }

    @Test
    @DisplayName("keeps a quote whose previous close is unknown, leaving percent change undefined")
    void keepsQuotesWithoutPreviousClose() {
        Map<String, Stock> upstream = Map.of("NEWCO", stock("NEWCO", "12.00", null));

        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class))).thenReturn(upstream);

            var quotes = adapter.findQuotes(List.of(Ticker.of("NEWCO")));

            assertThat(quotes).hasSize(1);
            assertThat(quotes.getFirst().percentChange()).isEmpty();
        }
    }

    @Test
    @DisplayName("translates an upstream I/O failure into a domain error")
    void translatesIoFailure() {
        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class)))
                    .thenThrow(new IOException("Server returned HTTP response code: 429"));

            assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                    .isInstanceOf(StockQuoteUnavailableException.class)
                    .hasMessageContaining("could not be retrieved")
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Test
    @DisplayName("translates an unchecked library failure into a domain error too")
    void translatesRuntimeFailure() {
        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class)))
                    .thenThrow(new IllegalStateException("malformed payload"));

            assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                    .isInstanceOf(StockQuoteUnavailableException.class);
        }
    }

    @Test
    @DisplayName("treats a null response as an outage rather than an empty result")
    void translatesNullResponse() {
        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            yahoo.when(() -> YahooFinance.get(Mockito.any(String[].class))).thenReturn(null);

            assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                    .isInstanceOf(StockQuoteUnavailableException.class);
        }
    }

    @Test
    @DisplayName("does not call the provider at all for an empty ticker list")
    void skipsEmptyRequests() {
        try (MockedStatic<YahooFinance> yahoo = Mockito.mockStatic(YahooFinance.class)) {
            assertThat(adapter.findQuotes(List.of())).isEmpty();

            yahoo.verifyNoInteractions();
        }
    }
}
