package com.forinvest.dashboard.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The only field a caller supplies: the symbol.
 *
 * <p>Case does not matter — it is upper-cased before it is stored — and the id and the creation
 * timestamp are assigned, never accepted from a client.
 */
@Schema(description = "The symbol to start watching")
public record AddWatchlistEntryRequest(
        @Schema(description = "Ticker symbol, in any case", example = "aapl", maxLength = 10)
        @NotBlank(message = "ticker must not be blank")
        @Size(max = 10, message = "ticker must be at most 10 characters")
        String ticker) {}
