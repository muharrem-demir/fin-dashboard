package com.forinvest.dashboard.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
 * The watchlist end to end: HTTP in, real PostgreSQL out.
 *
 * <p>Symbols are generated per test because the table is global — there is one watchlist, not one
 * per user — so a fixed ticker would make these tests depend on each other and on their order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WatchlistApiIT {

    @Autowired
    private MockMvc mockMvc;

    private static String uniqueTicker() {
        return "T" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    private String watch(String ticker) throws Exception {
        String body = mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"%s\"}".formatted(ticker)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    @DisplayName("walks the lifecycle: add lower case, list, delete")
    void supportsTheLifecycle() throws Exception {
        String ticker = uniqueTicker();

        // The rule that matters: whatever case the caller sends, the stored symbol is upper case.
        String id = watch(ticker.toLowerCase(java.util.Locale.ROOT));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].ticker".formatted(id)).value(ticker));

        mockMvc.perform(delete("/api/v1/watchlist/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).isEmpty());
    }

    @Test
    @DisplayName("a listed entry carries id and ticker, and nothing else")
    void listsIdAndTickerOnly() throws Exception {
        String id = watch(uniqueTicker());

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).isNotEmpty())
                // created_at is stored, but it is not part of the contract a listing promises.
                .andExpect(jsonPath("$..createdAt").isEmpty());
    }

    @Test
    @DisplayName("adding a ticker already watched returns a 409 problem document, in any case")
    void reportsDuplicateTicker() throws Exception {
        String ticker = uniqueTicker();
        watch(ticker);

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"%s\"}".formatted(ticker.toLowerCase(java.util.Locale.ROOT))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/ticker-already-watched"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(ticker)));
    }

    @Test
    @DisplayName("deleting an unknown entry returns a 404 problem document")
    void reportsMissingEntry() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlist/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/watchlist-entry-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("an unparseable ticker is rejected by the domain with a 400 problem document")
    void rejectsInvalidTicker() throws Exception {
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"1NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-ticker"));
    }
}
