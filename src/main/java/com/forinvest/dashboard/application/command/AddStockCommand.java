package com.forinvest.dashboard.application.command;

import java.util.UUID;

/**
 * Intent to add shares of a ticker to a portfolio.
 *
 * <p>The ticker arrives as a raw string; turning it into a {@code Ticker} is the domain's job, so
 * an invalid symbol is reported as a domain error rather than silently normalised here.
 */
public record AddStockCommand(UUID portfolioId, String ticker, int shares) {}
