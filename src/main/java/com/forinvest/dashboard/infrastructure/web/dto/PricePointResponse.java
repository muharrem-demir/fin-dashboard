package com.forinvest.dashboard.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One trading day of a ticker's price history")
public record PricePointResponse(
        @Schema(description = "Trading day, in the exchange's own timezone", example = "2026-08-14")
        LocalDate date,

        @Schema(description = "Closing price on that day", example = "150.25")
        BigDecimal close) {}
