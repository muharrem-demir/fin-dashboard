package com.forinvest.dashboard.infrastructure.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A market quote for one ticker")
public record StockQuoteResponse(
        @Schema(description = "Normalised ticker symbol", example = "AAPL")
        String ticker,

        @Schema(description = "Latest traded price", example = "150.25")
        BigDecimal price,

        @Schema(description = "Previous close, when the provider reports one", example = "148.50")
        BigDecimal previousClose,

        @Schema(
                description = "Change against the previous close, as a percentage rounded to two decimals."
                        + " Omitted when the previous close is unknown or zero.",
                example = "1.18")
        BigDecimal percentChange) {}
