package com.forinvest.dashboard.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "Shares to add to a portfolio. If the ticker is already held, the shares are added to the existing"
                        + " position rather than replacing it.")
public record AddStockRequest(
        @Schema(description = "Stock ticker symbol, case-insensitive", example = "AAPL", maxLength = 16)
        @NotBlank(message = "ticker must not be blank")
        @Size(max = 16, message = "ticker must be at most 16 characters")
        String ticker,

        @Schema(description = "Number of shares to add", example = "10", minimum = "1")
        @NotNull(message = "shares must be provided")
        @Positive(message = "shares must be greater than zero")
        Integer shares) {}
