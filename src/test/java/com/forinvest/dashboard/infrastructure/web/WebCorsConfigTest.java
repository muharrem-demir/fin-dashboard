package com.forinvest.dashboard.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.application.usecase.ListStockQuotesUseCase;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;

/**
 * The CORS policy as a browser sees it.
 *
 * <p>Asserted through a real endpoint rather than on the registry, because what matters is the
 * headers that come back on the wire — a mapping registered against the wrong path pattern would
 * still look correct in the configuration.
 */
@WebMvcTest(StockQuoteController.class)
@Import({WebCorsConfig.class, StockQuoteWebMapper.class, GlobalExceptionHandler.class})
class WebCorsConfigTest {

    private static final String ORIGIN = "https://app.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListStockQuotesUseCase listStockQuotes;

    @BeforeEach
    void stubUseCase() {
        Mockito.when(listStockQuotes.execute(any(ListStockQuotesQuery.class)))
                .thenReturn(StockQuoteLookup.reconcile(List.of(Ticker.of("AAPL")), List.of()));
    }

    @Test
    @DisplayName("answers a preflight with the allowed origin, methods and cache lifetime")
    void answersPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/stocks/quotes")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().stringValues(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("allows the actual request and exposes Location so a created resource can be found")
    void allowsActualRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location"));
    }

    @Test
    @DisplayName("does not claim to allow credentials while there is no authentication")
    void doesNotAllowCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("a request without an Origin is left alone")
    void leavesSameOriginRequestsAlone() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/quotes").param("tickers", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
