package com.forinvest.dashboard.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * The API end to end: HTTP in, real PostgreSQL out.
 *
 * <p>Nothing is mocked, so this is the test that proves the pieces agree — the Flyway schema, the
 * JPA mapping, the domain rules and the JSON contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortfolioApiIT {

    @Autowired
    private MockMvc mockMvc;

    private String createPortfolio(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void addStock(String portfolioId, String ticker, int shares) throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"%s\",\"shares\":%d}".formatted(ticker, shares)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("walks the full lifecycle: create, add, top up, remove, delete")
    void supportsTheFullLifecycle() throws Exception {
        String id = createPortfolio("Growth");

        addStock(id, "AAPL", 10);
        addStock(id, "MSFT", 20);

        // The rule that matters: the same ticker in a different case tops up the existing position.
        addStock(id, "aapl", 5);

        mockMvc.perform(get("/api/v1/portfolios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCount").value(2))
                .andExpect(jsonPath("$.totalShares").value(35))
                .andExpect(jsonPath("$.stocks[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.stocks[0].shares").value(15))
                .andExpect(jsonPath("$.stocks[1].ticker").value("MSFT"))
                .andExpect(jsonPath("$.stocks[1].shares").value(20));

        mockMvc.perform(delete("/api/v1/portfolios/{id}/stocks/{ticker}", id, "aapl"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCount").value(1))
                .andExpect(jsonPath("$.stocks[0].ticker").value("MSFT"));

        mockMvc.perform(delete("/api/v1/portfolios/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolios/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("removing a ticker the portfolio does not hold returns 404")
    void reportsMissingHolding() throws Exception {
        String id = createPortfolio("Growth");
        addStock(id, "AAPL", 10);

        mockMvc.perform(delete("/api/v1/portfolios/{id}/stocks/{ticker}", id, "AAPL"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/portfolios/{id}/stocks/{ticker}", id, "AAPL"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/stock-not-found"));
    }

    @Test
    @DisplayName("renaming preserves holdings")
    void renamesPortfolio() throws Exception {
        String id = createPortfolio("Growth");
        addStock(id, "AAPL", 10);

        mockMvc.perform(patch("/api/v1/portfolios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Income\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Income"))
                .andExpect(jsonPath("$.stocks[0].shares").value(10));
    }

    @Test
    @DisplayName("listing returns summaries for the stored portfolios")
    void listsPortfolios() throws Exception {
        String id = createPortfolio("Listable " + UUID.randomUUID());
        addStock(id, "AAPL", 7);

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.id == '%s')].totalShares".formatted(id)).value(7));
    }

    @Test
    @DisplayName("an unknown portfolio yields a 404 problem document")
    void reportsMissingPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/portfolio-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("a non-positive share count is rejected with a field-level validation error")
    void rejectsNonPositiveShares() throws Exception {
        String id = createPortfolio("Growth");

        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"AAPL\",\"shares\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shares"));
    }

    @Test
    @DisplayName("an unparseable ticker is rejected by the domain with a 400 problem document")
    void rejectsInvalidTicker() throws Exception {
        String id = createPortfolio("Growth");

        mockMvc.perform(post("/api/v1/portfolios/{id}/stocks", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"1NOPE!\",\"shares\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-ticker"));
    }

    /**
     * Verifies the generated specification and writes it to {@code target/openapi/openapi.json}.
     *
     * <p>CI publishes that file as a build artifact. Producing it from the running application —
     * rather than maintaining a checked-in copy — is what guarantees the published specification
     * describes the code that actually shipped.
     */
    @Test
    @DisplayName("the OpenAPI document is generated from the code")
    void publishesOpenApiDocument() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Path output = Path.of("target", "openapi");
        Files.createDirectories(output);
        Files.writeString(output.resolve("openapi.json"), document, StandardCharsets.UTF_8);

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Financial Portfolio Dashboard API"))
                .andExpect(jsonPath("$.paths['/api/v1/portfolios']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/portfolios/{portfolioId}/stocks']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/portfolios/{portfolioId}/stocks/{ticker}']")
                        .exists());
    }

    @Test
    @DisplayName("the health endpoint reports the database as up")
    void reportsHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
