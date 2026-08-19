package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** One watched symbol: its identity, and the symbol itself. */
@Schema(description = "A symbol on the watchlist")
public record WatchlistEntryResponse(
        @Schema(description = "Watchlist entry identifier") UUID id,

        @Schema(description = "Ticker symbol, upper case", example = "AAPL")
        String ticker) {}
