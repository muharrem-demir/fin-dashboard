package com.forinvest.dashboard.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "New name for an existing portfolio")
public record RenamePortfolioRequest(
        @Schema(description = "Display name of the portfolio", example = "Income", maxLength = 100)
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name) {}
