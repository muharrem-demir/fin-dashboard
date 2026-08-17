package com.forinvest.dashboard.application.query;

import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;

/**
 * A request for quotes on several tickers at once.
 *
 * <p>A query rather than a command: it changes nothing. Tickers stay raw strings here — turning
 * them into {@code Ticker} values is the domain's job, so a bad symbol is reported as a domain
 * error rather than normalised away at the edge.
 */
public record ListStockQuotesQuery(List<String> tickers) {

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
}
