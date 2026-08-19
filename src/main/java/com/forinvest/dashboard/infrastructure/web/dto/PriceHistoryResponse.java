package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recent daily closes for one ticker, oldest first")
public record PriceHistoryResponse(
        @Schema(description = "Normalised ticker symbol", example = "AAPL")
        String ticker,

        @Schema(
                description = "Trading days of history configured for this deployment. Fewer points than this"
                        + " means the provider had no more, not that days were dropped.",
                example = "5")
        int days,

        @Schema(description = "One point per trading day, oldest first")
        List<PricePointResponse> points) {}
