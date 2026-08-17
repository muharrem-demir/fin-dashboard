package com.forinvest.dashboard.application.command;

import java.util.UUID;

/** Intent to remove the whole position in a ticker from a portfolio. */
public record RemoveStockCommand(UUID portfolioId, String ticker) {}
