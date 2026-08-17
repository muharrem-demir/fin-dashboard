package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A portfolio without its holdings, for list views")
public record PortfolioSummaryResponse(
        @Schema(description = "Portfolio identifier") UUID id,

        @Schema(description = "Display name", example = "Growth")
        String name,

        @Schema(description = "Number of distinct tickers held", example = "2")
        int stockCount,

        @Schema(description = "Sum of shares across all holdings", example = "35")
        long totalShares) {}
