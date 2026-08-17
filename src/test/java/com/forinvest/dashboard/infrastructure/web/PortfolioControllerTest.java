package com.forinvest.dashboard.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.forinvest.dashboard.application.command.CreatePortfolioCommand;
import com.forinvest.dashboard.application.command.RenamePortfolioCommand;
import com.forinvest.dashboard.application.usecase.CreatePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.DeletePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.GetPortfolioUseCase;
import com.forinvest.dashboard.application.usecase.ListPortfoliosUseCase;
import com.forinvest.dashboard.application.usecase.RenamePortfolioUseCase;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;

/**
 * The HTTP contract of {@code /api/v1/portfolios}.
 *
 * <p>Use cases are mocked: this test is about status codes, JSON shape and error documents, not
 * about business rules — those are covered without a web layer in {@code PortfolioTest}.
 */
@WebMvcTest(PortfolioController.class)
@Import({PortfolioWebMapper.class, GlobalExceptionHandler.class})
class PortfolioControllerTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListPortfoliosUseCase listPortfolios;

    @MockitoBean
    private GetPortfolioUseCase getPortfolio;

    @MockitoBean
    private CreatePortfolioUseCase createPortfolio;

    @MockitoBean
    private RenamePortfolioUseCase renamePortfolio;

    @MockitoBean
    private DeletePortfolioUseCase deletePortfolio;

    @Test
    @DisplayName("GET /portfolios returns summaries without holdings")
    void listsPortfolios() throws Exception {
        when(listPortfolios.execute())
                .thenReturn(List.of(new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10)))));

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PORTFOLIO_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Growth"))
                .andExpect(jsonPath("$[0].stockCount").value(1))
                .andExpect(jsonPath("$[0].totalShares").value(10))
                .andExpect(jsonPath("$[0].stocks").doesNotExist());
    }

    @Test
    @DisplayName("GET /portfolios/{id} returns the portfolio with its holdings")
    void returnsPortfolio() throws Exception {
        when(getPortfolio.execute(PORTFOLIO_ID))
                .thenReturn(
                        new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10), Holding.of("MSFT", 20))));

        mockMvc.perform(get("/api/v1/portfolios/{id}", PORTFOLIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(2))
                .andExpect(jsonPath("$.stocks[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.stocks[0].shares").value(10))
                .andExpect(jsonPath("$.totalShares").value(30));
    }

    @Test
    @DisplayName("GET /portfolios/{id} returns a 404 problem document for an unknown id")
    void returnsProblemDetailWhenPortfolioIsMissing() throws Exception {
        when(getPortfolio.execute(PORTFOLIO_ID)).thenThrow(new PortfolioNotFoundException(PORTFOLIO_ID));

        mockMvc.perform(get("/api/v1/portfolios/{id}", PORTFOLIO_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/portfolio-not-found"))
                .andExpect(jsonPath("$.title").value("Portfolio not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(PORTFOLIO_ID.toString())))
                .andExpect(jsonPath("$.instance").value("/api/v1/portfolios/" + PORTFOLIO_ID))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /portfolios/{id} rejects a malformed UUID with a 400 problem document")
    void rejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-parameter"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /portfolios returns 201 with a Location header")
    void createsPortfolio() throws Exception {
        when(createPortfolio.execute(any(CreatePortfolioCommand.class)))
                .thenReturn(new Portfolio(PORTFOLIO_ID, "Growth", List.of()));

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Growth\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location", org.hamcrest.Matchers.endsWith("/api/v1/portfolios/" + PORTFOLIO_ID)))
                .andExpect(jsonPath("$.name").value("Growth"))
                .andExpect(jsonPath("$.stocks").isEmpty());
    }

    @Test
    @DisplayName("POST /portfolios reports a blank name as a field-level validation error")
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("name must not be blank"));

        verify(createPortfolio, never()).execute(any());
    }

    @Test
    @DisplayName("PATCH /portfolios/{id} renames the portfolio")
    void renamesPortfolio() throws Exception {
        when(renamePortfolio.execute(any(RenamePortfolioCommand.class)))
                .thenReturn(new Portfolio(PORTFOLIO_ID, "Income", List.of()));

        mockMvc.perform(patch("/api/v1/portfolios/{id}", PORTFOLIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Income\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Income"));
    }

    @Test
    @DisplayName("DELETE /portfolios/{id} returns 204")
    void deletesPortfolio() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{id}", PORTFOLIO_ID)).andExpect(status().isNoContent());

        verify(deletePortfolio).execute(PORTFOLIO_ID);
    }

    @Test
    @DisplayName("DELETE /portfolios/{id} returns 404 for an unknown id")
    void deleteReportsMissingPortfolio() throws Exception {
        org.mockito.Mockito.doThrow(new PortfolioNotFoundException(PORTFOLIO_ID))
                .when(deletePortfolio)
                .execute(PORTFOLIO_ID);

        mockMvc.perform(delete("/api/v1/portfolios/{id}", PORTFOLIO_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Portfolio not found"));
    }
}
