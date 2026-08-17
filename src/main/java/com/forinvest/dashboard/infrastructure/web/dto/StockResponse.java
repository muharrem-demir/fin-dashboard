package com.forinvest.dashboard.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A position in a single ticker")
public record StockResponse(
        @Schema(description = "Normalised ticker symbol", example = "AAPL")
        String ticker,

        @Schema(description = "Number of shares held", example = "15")
        int shares) {}
