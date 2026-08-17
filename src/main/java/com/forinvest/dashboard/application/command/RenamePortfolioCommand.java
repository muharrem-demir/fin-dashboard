package com.forinvest.dashboard.application.command;

import java.util.UUID;

/** Intent to rename an existing portfolio. */
public record RenamePortfolioCommand(UUID portfolioId, String name) {}
