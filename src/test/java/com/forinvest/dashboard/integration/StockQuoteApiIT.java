package com.forinvest.dashboard.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.PricePoint;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

/**
 * The quotes endpoint through the real application context.
 *
 * <p>The {@link StockQuoteProvider} port is replaced rather than calling Yahoo: a build must not
 * fail because a third party rate-limited us, and market data is not deterministic enough to assert
 * on. Everything on our side of the port is real — routing, the use case, the domain calculation,
 * JSON serialisation and the error handler.
 *
 * <p>The history provider is replaced for the same reasons, and replacing it is also what keeps a
 * request for {@code history=true} from reaching Yahoo one call per ticker.
 *
 * <p>{@link YahooFinanceStockQuoteAdapterTest} covers the adapter itself, so the only untested link
 * is the live Yahoo call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StockQuoteApiIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockQuoteProvider stockQuoteProvider;

    @MockitoBean
    private StockPriceHistoryProvider stockPriceHistoryProvider;

    private static StockQuote quote(String ticker, String price, String previousClose) {
        return new StockQuote(
                Ticker.of(ticker), new BigDecimal(price), previousClose == null ? null : new BigDecimal(previousClose));
    }

    private static PriceHistory history(String ticker, HistoryWindow window, String... closes) {
        List<PricePoint> points = new java.util.ArrayList<>();
        for (int day = 0; day < closes.length; day++) {
            points.add(
                    new PricePoint(java.time.LocalDate.parse("2026-08-10").plusDays(day), new BigDecimal(closes[day])));
        }
        return PriceHistory.of(Ticker.of(ticker), points, window);
    }

    @Test
    @DisplayName("returns quotes and percent change for a batch of tickers in one provider call")
    void returnsQuotesForABatch() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenReturn(List.of(quote("AAPL", "150.25", "148.50"), quote("MSFT", "198.00", "200.00")));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.quoteCount").value(2))
                .andExpect(jsonPath("$.quotes[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.quotes[0].price").value(150.25))
                // (150.25 - 148.50) / 148.50 * 100
                .andExpect(jsonPath("$.quotes[0].percentChange").value(1.18))
                .andExpect(jsonPath("$.quotes[1].ticker").value("MSFT"))
                // (198.00 - 200.00) / 200.00 * 100
                .andExpect(jsonPath("$.quotes[1].percentChange").value(-1.00));

        ArgumentCaptor<List<Ticker>> requested = ArgumentCaptor.captor();
        verify(stockQuoteProvider, times(1)).findQuotes(requested.capture());
        org.assertj.core.api.Assertions.assertThat(requested.getValue())
                .containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));
    }

    @Test
    @DisplayName("collapses case-insensitive duplicates into one upstream symbol")
    void deduplicatesTickers() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "150.00", "150.00")));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCount").value(1));

        ArgumentCaptor<List<Ticker>> requested = ArgumentCaptor.captor();
        verify(stockQuoteProvider).findQuotes(requested.capture());
        org.assertj.core.api.Assertions.assertThat(requested.getValue()).containsExactly(Ticker.of("AAPL"));
    }

    @Test
    @DisplayName("lists unknown tickers instead of silently dropping them")
    void reportsUnresolvedTickers() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "150.00", "150.00")));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,NOSUCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCount").value(1))
                .andExpect(jsonPath("$.unresolved[0]").value("NOSUCH"));
    }

    @Test
    @DisplayName("rejects an invalid symbol with a 400 problem document")
    void rejectsInvalidTicker() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "1BAD!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-ticker"));
    }

    @Test
    @DisplayName("rejects a batch over the supported maximum with a 400 problem document")
    void rejectsOversizedBatch() throws Exception {
        String tooMany = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj("TICK%d"::formatted)
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-quote-request"))
                .andExpect(jsonPath("$.title").value("Invalid quote request"));
    }

    @Test
    @DisplayName("surfaces a provider outage as 502 with a problem document, not a 500")
    void reportsProviderOutage() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList()))
                .thenThrow(new StockQuoteUnavailableException("Stock quotes could not be retrieved"));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/stock-quotes-unavailable"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("does not ask for history, or return any, unless the request opts in")
    void omitsHistoryUnlessAsked() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "150.25", "148.50")));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").doesNotExist());

        org.mockito.Mockito.verifyNoInteractions(stockPriceHistoryProvider);
    }

    @Test
    @DisplayName("returns daily closes for the configured window when history=true")
    void returnsHistoryWhenAsked() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "150.25", "148.50")));
        ArgumentCaptor<HistoryWindow> window = ArgumentCaptor.captor();
        when(stockPriceHistoryProvider.findHistories(anyList(), any(HistoryWindow.class)))
                .thenAnswer(invocation ->
                        List.of(history("AAPL", invocation.getArgument(1), "146.00", "148.50", "150.25")));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL").param("history", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].percentChange").value(1.18))
                .andExpect(jsonPath("$.history[0].ticker").value("AAPL"))
                // The default configured in application.yml.
                .andExpect(jsonPath("$.history[0].days").value(5))
                .andExpect(jsonPath("$.history[0].points.length()").value(3))
                .andExpect(jsonPath("$.history[0].points[0].date").value("2026-08-10"))
                .andExpect(jsonPath("$.history[0].points[2].close").value(150.25));

        ArgumentCaptor<List<Ticker>> requested = ArgumentCaptor.captor();
        verify(stockPriceHistoryProvider, times(1)).findHistories(requested.capture(), window.capture());
        org.assertj.core.api.Assertions.assertThat(requested.getValue()).containsExactly(Ticker.of("AAPL"));
        org.assertj.core.api.Assertions.assertThat(window.getValue().days()).isEqualTo(5);
    }

    @Test
    @DisplayName("surfaces a history outage as 502, like any other provider failure")
    void reportsHistoryOutage() throws Exception {
        when(stockQuoteProvider.findQuotes(anyList())).thenReturn(List.of(quote("AAPL", "150.25", "148.50")));
        when(stockPriceHistoryProvider.findHistories(anyList(), any(HistoryWindow.class)))
                .thenThrow(new StockQuoteUnavailableException("Stock price history could not be retrieved"));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL").param("history", "true"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/stock-quotes-unavailable"));
    }

    @Test
    @DisplayName("the quotes endpoint appears in the generated OpenAPI document")
    void isDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/quotes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/quotes'].get.responses['502']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/quotes'].get.parameters[*].name")
                        .value(org.hamcrest.Matchers.hasItems("tickers", "history")));
    }
}
