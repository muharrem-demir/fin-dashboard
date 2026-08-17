package com.forinvest.dashboard.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.forinvest.dashboard.application.command.AddStockCommand;
import com.forinvest.dashboard.application.command.RemoveStockCommand;
import com.forinvest.dashboard.application.usecase.AddStockUseCase;
import com.forinvest.dashboard.application.usecase.RemoveStockUseCase;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;
import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;

/** The HTTP contract of {@code /api/v1/portfolios/{id}/stocks}. */
@WebMvcTest(PortfolioStockController.class)
@Import({PortfolioWebMapper.class, GlobalExceptionHandler.class})
class PortfolioStockControllerTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddStockUseCase addStock;

    @MockitoBean
    private RemoveStockUseCase removeStock;

    @Test
    @DisplayName("POST /stocks returns the updated portfolio")
    void addsStock() throws Exception {
        when(addStock.execute(any(AddStockCommand.class)))
                .thenReturn(new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 15))));

        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"AAPL\",\"shares\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.stocks[0].shares").value(15));

        ArgumentCaptor<AddStockCommand> command = ArgumentCaptor.forClass(AddStockCommand.class);
        verify(addStock).execute(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue())
                .isEqualTo(new AddStockCommand(PORTFOLIO_ID, "AAPL", 5));
    }

    @Test
    @DisplayName("POST /stocks rejects a non-positive share count before reaching the use case")
    void rejectsNonPositiveShares() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"AAPL\",\"shares\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("shares"))
                .andExpect(jsonPath("$.errors[0].message").value("shares must be greater than zero"));

        verify(addStock, never()).execute(any());
    }

    @Test
    @DisplayName("POST /stocks rejects a missing ticker")
    void rejectsBlankTicker() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"\",\"shares\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ticker"));

        verify(addStock, never()).execute(any());
    }

    @Test
    @DisplayName("POST /stocks surfaces a domain ticker rejection as a 400 problem document")
    void reportsInvalidTickerFromDomain() throws Exception {
        when(addStock.execute(any(AddStockCommand.class)))
                .thenThrow(new InvalidTickerException("Ticker 'A B' is not a valid symbol"));

        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"A B\",\"shares\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-ticker"))
                .andExpect(jsonPath("$.title").value("Invalid ticker"));
    }

    @Test
    @DisplayName("DELETE /stocks/{ticker} returns 204")
    void removesStock() throws Exception {
        when(removeStock.execute(any(RemoveStockCommand.class)))
                .thenReturn(new Portfolio(PORTFOLIO_ID, "Growth", List.of()));

        mockMvc.perform(delete("/api/v1/portfolios/{id}/stocks/{ticker}", PORTFOLIO_ID, "AAPL"))
                .andExpect(status().isNoContent());

        verify(removeStock).execute(new RemoveStockCommand(PORTFOLIO_ID, "AAPL"));
    }

    @Test
    @DisplayName("DELETE /stocks/{ticker} returns 404 when the portfolio does not hold the ticker")
    void reportsMissingHolding() throws Exception {
        when(removeStock.execute(any(RemoveStockCommand.class)))
                .thenThrow(new StockNotFoundException(PORTFOLIO_ID, Ticker.of("MSFT")));

        mockMvc.perform(delete("/api/v1/portfolios/{id}/stocks/{ticker}", PORTFOLIO_ID, "MSFT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/stock-not-found"))
                .andExpect(jsonPath("$.title").value("Stock not found"));
    }
}
