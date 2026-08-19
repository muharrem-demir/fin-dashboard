package com.forinvest.dashboard.application.command;

/**
 * Intent to start watching a symbol.
 *
 * <p>The ticker stays a {@code String} here: turning it into a {@code Ticker} — and upper-casing it
 * on the way — is the domain's job, so an unparseable symbol is reported as a domain error rather
 * than as a binding failure.
 */
public record AddWatchlistEntryCommand(String ticker) {}
