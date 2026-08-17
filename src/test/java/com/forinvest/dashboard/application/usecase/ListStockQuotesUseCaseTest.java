package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

@ExtendWith(MockitoExtension.class)
class ListStockQuotesUseCaseTest {

    @Mock
    private StockQuoteProvider stockQuoteProvider;

    @Captor
    private ArgumentCaptor<List<Ticker>> requestedTickers;

    private ListStockQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListStockQuotesUseCase(stockQuoteProvider);
    }

    private static StockQuote quote(String ticker, String price, String previousClose) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal(price), new BigDecimal(previousClose));
    }

    @Test
    @DisplayName("asks the provider for every ticker in a single call")
    void batchesIntoOneProviderCall() {
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenReturn(List.of(quote("AAPL", "110", "100"), quote("MSFT", "198", "200")));

        StockQuoteLookup result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "MSFT")));

        verify(stockQuoteProvider, times(1)).findQuotes(requestedTickers.capture());
        assertThat(requestedTickers.getValue()).containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));
        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().getFirst().percentChange()).contains(new BigDecimal("10.00"));
        assertThat(result.quotes().get(1).percentChange()).contains(new BigDecimal("-1.00"));
    }

    @Test
    @DisplayName("de-duplicates tickers that differ only by case, so the provider is not asked twice")
    void deduplicatesTickers() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));

        useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "aapl", " AAPL ")));

        verify(stockQuoteProvider).findQuotes(requestedTickers.capture());
        assertThat(requestedTickers.getValue()).containsExactly(Ticker.of("AAPL"));
    }

    @Test
    @DisplayName("reports tickers the provider did not know")
    void reportsUnresolvedTickers() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));

        StockQuoteLookup result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "NOSUCH")));

        assertThat(result.quotes()).hasSize(1);
        assertThat(result.unresolved()).containsExactly(Ticker.of("NOSUCH"));
    }

    @Test
    @DisplayName("rejects an invalid ticker before calling the provider")
    void rejectsInvalidTicker() {
        ListStockQuotesQuery query = new ListStockQuotesQuery(List.of("AAPL", "not a ticker"));

        assertThatThrownBy(() -> useCase.execute(query)).isInstanceOf(InvalidTickerException.class);

        verify(stockQuoteProvider, never()).findQuotes(anyList());
    }

    @Test
    @DisplayName("lets a provider outage surface as an unavailable-quotes failure")
    void propagatesProviderFailure() {
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenThrow(new StockQuoteUnavailableException("upstream refused the request"));
        ListStockQuotesQuery query = new ListStockQuotesQuery(List.of("AAPL"));

        assertThatThrownBy(() -> useCase.execute(query)).isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("rejects an empty ticker list")
    void rejectsEmptyRequest() {
        assertThatThrownBy(() -> new ListStockQuotesQuery(List.of()))
                .isInstanceOf(InvalidQuoteRequestException.class)
                .hasMessageContaining("At least one ticker");
    }

    @Test
    @DisplayName("rejects a batch larger than the supported maximum")
    void rejectsOversizedRequest() {
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, ListStockQuotesQuery.MAX_TICKERS + 1)
                .mapToObj("T%d"::formatted)
                .toList();

        assertThatThrownBy(() -> new ListStockQuotesQuery(tooMany))
                .isInstanceOf(InvalidQuoteRequestException.class)
                .hasMessageContaining("At most");
    }

    @Test
    @DisplayName("rejects a null query")
    void rejectsNullQuery() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
