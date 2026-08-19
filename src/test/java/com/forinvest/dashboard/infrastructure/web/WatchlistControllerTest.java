package com.forinvest.dashboard.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.forinvest.dashboard.application.command.AddWatchlistEntryCommand;
import com.forinvest.dashboard.application.usecase.AddWatchlistEntryUseCase;
import com.forinvest.dashboard.application.usecase.ListWatchlistUseCase;
import com.forinvest.dashboard.application.usecase.RemoveWatchlistEntryUseCase;
import com.forinvest.dashboard.domain.exception.TickerAlreadyWatchedException;
import com.forinvest.dashboard.domain.exception.WatchlistEntryNotFoundException;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;

/**
 * The HTTP contract of {@code /api/v1/watchlist}.
 *
 * <p>Use cases are mocked: this is about status codes, JSON shape and problem documents. The
 * upper-casing and duplicate rules are covered without a web layer in {@code WatchlistEntryTest} and
 * {@code AddWatchlistEntryUseCaseTest}.
 */
@WebMvcTest(WatchlistController.class)
@Import({WatchlistWebMapper.class, GlobalExceptionHandler.class})
class WatchlistControllerTest {

    private static final UUID ENTRY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListWatchlistUseCase listWatchlist;

    @MockitoBean
    private AddWatchlistEntryUseCase addEntry;

    @MockitoBean
    private RemoveWatchlistEntryUseCase removeEntry;

    @Test
    @DisplayName("GET /watchlist returns id and ticker, and nothing else")
    void listsWatchlist() throws Exception {
        when(listWatchlist.execute()).thenReturn(List.of(new WatchlistEntry(ENTRY_ID, Ticker.of("AAPL"))));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ENTRY_ID.toString()))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].length()").value(2));
    }

    @Test
    @DisplayName("POST /watchlist returns 201 with a Location header")
    void addsEntry() throws Exception {
        when(addEntry.execute(any(AddWatchlistEntryCommand.class)))
                .thenReturn(new WatchlistEntry(ENTRY_ID, Ticker.of("AAPL")));

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"aapl\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/watchlist/" + ENTRY_ID)))
                .andExpect(jsonPath("$.id").value(ENTRY_ID.toString()))
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    @DisplayName("POST /watchlist reports a blank ticker as a field-level validation error")
    void rejectsBlankTicker() throws Exception {
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("ticker"))
                .andExpect(jsonPath("$.errors[0].message").value("ticker must not be blank"));

        verify(addEntry, never()).execute(any());
    }

    @Test
    @DisplayName("POST /watchlist returns a 409 problem document for a ticker already watched")
    void reportsDuplicate() throws Exception {
        when(addEntry.execute(any(AddWatchlistEntryCommand.class)))
                .thenThrow(new TickerAlreadyWatchedException(Ticker.of("AAPL")));

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"AAPL\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/ticker-already-watched"))
                .andExpect(jsonPath("$.title").value("Ticker already exists"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("AAPL")))
                .andExpect(jsonPath("$.instance").value("/api/v1/watchlist"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("DELETE /watchlist/{id} returns 204")
    void removesEntry() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlist/{id}", ENTRY_ID)).andExpect(status().isNoContent());

        verify(removeEntry).execute(ENTRY_ID);
    }

    @Test
    @DisplayName("DELETE /watchlist/{id} returns 404 for an unknown id")
    void reportsMissingEntry() throws Exception {
        doThrow(new WatchlistEntryNotFoundException(ENTRY_ID)).when(removeEntry).execute(ENTRY_ID);

        mockMvc.perform(delete("/api/v1/watchlist/{id}", ENTRY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/watchlist-entry-not-found"))
                .andExpect(jsonPath("$.title").value("Watchlist entry not found"));
    }

    @Test
    @DisplayName("DELETE /watchlist/{id} rejects a malformed UUID with a 400 problem document")
    void rejectsMalformedId() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlist/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.forinvest.com/problems/invalid-parameter"));
    }
}
