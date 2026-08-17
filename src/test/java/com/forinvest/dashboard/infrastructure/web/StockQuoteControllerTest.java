package com.forinvest.dashboard.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.application.usecase.ListStockQuotesUseCase;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;

/** The HTTP contract of {@code /api/v1/stocks/quotes}. */
@WebMvcTest(StockQuoteController.class)
@Import({StockQuoteWebMapper.class, GlobalExceptionHandler.class})
class StockQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListStockQuotesUseCase listStockQuotes;

    private static StockQuote quote(String ticker, String price, String previousClose) {
        return new StockQuote(
                Ticker.of(ticker), new BigDecimal(price), previousClose == null ? null : new BigDecimal(previousClose));
    }

    @Test
    @DisplayName("returns price and percent change for each requested ticker")
    void returnsQuotes() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(
                        List.of(Ticker.of("AAPL"), Ticker.of("MSFT")),
                        List.of(quote("AAPL", "150.25", "148.50"), quote("MSFT", "198.00", "200.00"))));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCount").value(2))
                .andExpect(jsonPath("$.quotes[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.quotes[0].price").value(150.25))
                .andExpect(jsonPath("$.quotes[0].previousClose").value(148.50))
                .andExpect(jsonPath("$.quotes[0].percentChange").value(1.18))
                .andExpect(jsonPath("$.quotes[1].ticker").value("MSFT"))
                .andExpect(jsonPath("$.quotes[1].percentChange").value(-1.00))
                .andExpect(jsonPath("$.unresolved").isEmpty());
    }

    @Test
    @DisplayName("passes the requested tickers through to the use case")
    void passesTickersToUseCase() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(List.of(), List.of()));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,MSFT,TSLA"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListStockQuotesQuery> query = ArgumentCaptor.forClass(ListStockQuotesQuery.class);
        verify(listStockQuotes).execute(query.capture());
        org.assertj.core.api.Assertions.assertThat(query.getValue().tickers()).containsExactly("AAPL", "MSFT", "TSLA");
    }

    @Test
    @DisplayName("accepts repeated tickers parameters as well as a comma-separated list")
    void acceptsRepeatedParameters() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(List.of(), List.of()));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL").param("tickers", "MSFT"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListStockQuotesQuery> query = ArgumentCaptor.forClass(ListStockQuotesQuery.class);
        verify(listStockQuotes).execute(query.capture());
        org.assertj.core.api.Assertions.assertThat(query.getValue().tickers()).containsExactly("AAPL", "MSFT");
    }

    @Test
    @DisplayName("omits percentChange when the previous close is unknown")
    void omitsUndefinedPercentChange() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(
                        List.of(Ticker.of("NEWCO")), List.of(quote("NEWCO", "12.00", null))));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "NEWCO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].percentChange").doesNotExist())
                .andExpect(jsonPath("$.quotes[0].previousClose").doesNotExist());
    }

    @Test
    @DisplayName("lists tickers the provider had no data for")
    void listsUnresolvedTickers() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(
                        List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH")), List.of(quote("AAPL", "150.00", "150.00"))));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL,NOSUCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCount").value(1))
                .andExpect(jsonPath("$.unresolved[0]").value("NOSUCH"));
    }

    @Test
    @DisplayName("requires the tickers parameter")
    void requiresTickersParameter() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/quotes")).andExpect(status().isBadRequest());

        verify(listStockQuotes, never()).execute(any());
    }

    @Test
    @DisplayName("reports an invalid symbol as a 400 problem document")
    void reportsInvalidTicker() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenThrow(new InvalidTickerException("Ticker '1BAD!' is not a valid symbol"));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "1BAD!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-ticker"));
    }

    @Test
    @DisplayName("reports a provider outage as 502, not 500")
    void reportsProviderOutageAsBadGateway() throws Exception {
        when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenThrow(new StockQuoteUnavailableException("Stock quotes could not be retrieved"));

        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/stock-quotes-unavailable"))
                .andExpect(jsonPath("$.title").value("Stock quotes unavailable"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
