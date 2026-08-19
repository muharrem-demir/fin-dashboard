package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.PricePoint;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteSnapshot;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

@ExtendWith(MockitoExtension.class)
class ListStockQuotesUseCaseTest {

    private static final HistoryWindow WINDOW = HistoryWindow.ofDays(5);

    @Mock
    private StockQuoteProvider stockQuoteProvider;

    @Mock
    private StockPriceHistoryProvider stockPriceHistoryProvider;

    @Captor
    private ArgumentCaptor<List<Ticker>> requestedTickers;

    private ListStockQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListStockQuotesUseCase(stockQuoteProvider, stockPriceHistoryProvider, WINDOW);
    }

    private static StockQuote quote(String ticker, String price, String previousClose) {
        return new StockQuote(Ticker.of(ticker), new BigDecimal(price), new BigDecimal(previousClose));
    }

    private static PriceHistory history(String ticker) {
        return PriceHistory.of(
                Ticker.of(ticker),
                List.of(new PricePoint(LocalDate.parse("2026-08-14"), new BigDecimal("110"))),
                WINDOW);
    }

    @Test
    @DisplayName("asks the provider for every ticker in a single call")
    void batchesIntoOneProviderCall() {
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenReturn(List.of(quote("AAPL", "110", "100"), quote("MSFT", "198", "200")));

        StockQuoteSnapshot result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "MSFT")));

        verify(stockQuoteProvider, times(1)).findQuotes(requestedTickers.capture());
        assertThat(requestedTickers.getValue()).containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));
        assertThat(result.quotes().quotes()).hasSize(2);
        assertThat(result.quotes().quotes().getFirst().percentChange()).contains(new BigDecimal("10.00"));
        assertThat(result.quotes().quotes().get(1).percentChange()).contains(new BigDecimal("-1.00"));
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

        StockQuoteSnapshot result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "NOSUCH")));

        assertThat(result.quotes().quotes()).hasSize(1);
        assertThat(result.quotes().unresolved()).containsExactly(Ticker.of("NOSUCH"));
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
    @DisplayName("does not touch the history provider unless the query asks for history")
    void skipsHistoryUnlessAsked() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));

        StockQuoteSnapshot result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL")));

        assertThat(result.historyIncluded()).isFalse();
        verifyNoInteractions(stockPriceHistoryProvider);
    }

    @Test
    @DisplayName("fetches history for the same de-duplicated tickers, using the configured window")
    void fetchesHistoryForTheSameTickers() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));
        when(stockPriceHistoryProvider.findHistories(anyList(), eq(WINDOW))).thenReturn(List.of(history("AAPL")));

        StockQuoteSnapshot result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL", "aapl"), true));

        verify(stockPriceHistoryProvider).findHistories(requestedTickers.capture(), eq(WINDOW));
        assertThat(requestedTickers.getValue()).containsExactly(Ticker.of("AAPL"));
        assertThat(result.historyIncluded()).isTrue();
        assertThat(result.histories()).hasSize(1);
        assertThat(result.histories().getFirst().window()).isEqualTo(WINDOW);
    }

    @Test
    @DisplayName("still answers with quotes and an empty history when the provider has none")
    void reportsMissingHistory() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));
        when(stockPriceHistoryProvider.findHistories(anyList(), eq(WINDOW))).thenReturn(List.of());

        StockQuoteSnapshot result = useCase.execute(new ListStockQuotesQuery(List.of("AAPL"), true));

        assertThat(result.quotes().quotes()).hasSize(1);
        assertThat(result.historyIncluded()).isTrue();
        assertThat(result.histories()).isEmpty();
    }

    @Test
    @DisplayName("lets a history outage surface as an unavailable-quotes failure")
    void propagatesHistoryFailure() {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "110", "100")));
        when(stockPriceHistoryProvider.findHistories(anyList(), eq(WINDOW)))
                .thenThrow(new StockQuoteUnavailableException("upstream refused the history request"));
        ListStockQuotesQuery query = new ListStockQuotesQuery(List.of("AAPL"), true);

        assertThatThrownBy(() -> useCase.execute(query)).isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("rejects a null query")
    void rejectsNullQuery() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
