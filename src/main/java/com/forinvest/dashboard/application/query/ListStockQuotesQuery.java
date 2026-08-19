package com.forinvest.dashboard.application.query;

import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;

/**
 * A request for quotes on several tickers at once, optionally with their recent price history.
 *
 * <p>A query rather than a command: it changes nothing. Tickers stay raw strings here — turning
 * them into {@code Ticker} values is the domain's job, so a bad symbol is reported as a domain
 * error rather than normalised away at the edge.
 *
 * <p>{@code includeHistory} is a yes/no and carries no length: how far back a history goes is
 * configuration, not something a caller may dial up per request. Otherwise one request could ask
 * the history provider for years of data on fifty symbols.
 */
public record ListStockQuotesQuery(List<String> tickers, boolean includeHistory) {

    /**
     * Upper bound on one batch.
     *
     * <p>Every request becomes a single upstream call, so an unbounded list would let one caller
     * hand the provider an arbitrarily large query on our behalf.
     */
    public static final int MAX_TICKERS = 50;

    public ListStockQuotesQuery {
        Objects.requireNonNull(tickers, "tickers must not be null");
        tickers = List.copyOf(tickers);
        if (tickers.isEmpty()) {
            throw InvalidQuoteRequestException.empty();
        }
        if (tickers.size() > MAX_TICKERS) {
            throw InvalidQuoteRequestException.tooMany(tickers.size(), MAX_TICKERS);
        }
    }

    /** Quotes only — history is opt-in, so this is what a caller that says nothing about it gets. */
    public ListStockQuotesQuery(List<String> tickers) {
        this(tickers, false);
    }
}
