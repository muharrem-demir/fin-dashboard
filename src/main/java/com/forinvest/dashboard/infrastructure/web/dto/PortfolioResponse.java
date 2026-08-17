package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A portfolio with all of its holdings")
public record PortfolioResponse(
        @Schema(description = "Portfolio identifier") UUID id,

        @Schema(description = "Display name", example = "Growth")
        String name,

        @Schema(description = "Holdings, in the order they were first added")
        List<StockResponse> stocks,

        @Schema(description = "Number of distinct tickers held", example = "2")
        int stockCount,

        @Schema(description = "Sum of shares across all holdings", example = "35")
        long totalShares) {}
