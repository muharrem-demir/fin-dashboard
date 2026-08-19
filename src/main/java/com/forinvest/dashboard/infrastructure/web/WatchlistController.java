package com.forinvest.dashboard.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.forinvest.dashboard.application.command.AddWatchlistEntryCommand;
import com.forinvest.dashboard.application.usecase.AddWatchlistEntryUseCase;
import com.forinvest.dashboard.application.usecase.ListWatchlistUseCase;
import com.forinvest.dashboard.application.usecase.RemoveWatchlistEntryUseCase;
import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.infrastructure.web.dto.AddWatchlistEntryRequest;
import com.forinvest.dashboard.infrastructure.web.dto.WatchlistEntryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * HTTP entry point for the watchlist.
 *
 * <p>Thin by design, exactly like {@link PortfolioController}: convert the request to a command,
 * call one use case, map the result. Failures propagate to {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/watchlist")
@Tag(name = "Watchlist", description = "Follow symbols outside of any portfolio")
class WatchlistController {

    private final ListWatchlistUseCase listWatchlist;
    private final AddWatchlistEntryUseCase addEntry;
    private final RemoveWatchlistEntryUseCase removeEntry;
    private final WatchlistWebMapper mapper;

    WatchlistController(
            ListWatchlistUseCase listWatchlist,
            AddWatchlistEntryUseCase addEntry,
            RemoveWatchlistEntryUseCase removeEntry,
            WatchlistWebMapper mapper) {
        this.listWatchlist = listWatchlist;
        this.addEntry = addEntry;
        this.removeEntry = removeEntry;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(
            summary = "List the watchlist",
            description = "Returns every watched symbol with its id, oldest entry first.")
    @ApiResponse(responseCode = "200", description = "Watchlist returned")
    ResponseEntity<List<WatchlistEntryResponse>> listWatchlist() {
        List<WatchlistEntryResponse> body =
                listWatchlist.execute().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping
    @Operation(
            summary = "Watch a symbol",
            description = "Adds one symbol to the watchlist. The symbol is stored in upper case.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Symbol added"),
        @ApiResponse(responseCode = "400", description = "Invalid ticker", content = @Content),
        @ApiResponse(responseCode = "409", description = "Ticker is already on the watchlist", content = @Content)
    })
    ResponseEntity<WatchlistEntryResponse> addEntry(
            @Valid @RequestBody AddWatchlistEntryRequest request, UriComponentsBuilder uriBuilder) {
        WatchlistEntry added = addEntry.execute(new AddWatchlistEntryCommand(request.ticker()));
        URI location = uriBuilder
                .path("/api/v1/watchlist/{entryId}")
                .buildAndExpand(added.id())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(added));
    }

    @DeleteMapping("/{entryId}")
    @Operation(summary = "Stop watching a symbol", description = "Removes one entry from the watchlist by its id.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Entry removed"),
        @ApiResponse(responseCode = "404", description = "No watchlist entry with this id", content = @Content)
    })
    ResponseEntity<Void> removeEntry(@PathVariable UUID entryId) {
        removeEntry.execute(entryId);
        return ResponseEntity.noContent().build();
    }
}
