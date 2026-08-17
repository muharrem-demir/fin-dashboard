package com.forinvest.dashboard.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The result of asking for several quotes at once.
 *
 * <p>A batch lookup is partially successful by nature: an upstream provider may know some of the
 * requested symbols and not others. Reporting the unknown ones explicitly, rather than silently
 * returning a shorter list, lets a dashboard tell "no data for this ticker" apart from "this
 * ticker was never asked for".
 */
public record StockQuoteLookup(List<StockQuote> quotes, List<Ticker> unresolved) {

    public StockQuoteLookup {
        Objects.requireNonNull(quotes, "quotes must not be null");
        Objects.requireNonNull(unresolved, "unresolved must not be null");
        quotes = List.copyOf(quotes);
        unresolved = List.copyOf(unresolved);
    }

    /**
     * Reconciles what was asked for against what came back.
     *
     * <p>Quotes are returned in the order the tickers were requested, so the caller's ordering is
     * preserved regardless of what order the provider answered in. Anything requested but not
     * quoted is reported as unresolved.
     */
    public static StockQuoteLookup reconcile(List<Ticker> requested, List<StockQuote> found) {
        Objects.requireNonNull(requested, "requested must not be null");
        Objects.requireNonNull(found, "found must not be null");

        List<StockQuote> ordered = requested.stream()
                .flatMap(ticker -> found.stream()
                        .filter(quote -> quote.ticker().equals(ticker))
                        .limit(1))
                .toList();

        Set<Ticker> quoted = ordered.stream().map(StockQuote::ticker).collect(java.util.stream.Collectors.toSet());
        List<Ticker> missing =
                requested.stream().filter(ticker -> !quoted.contains(ticker)).toList();

        return new StockQuoteLookup(ordered, missing);
    }

    public boolean isEmpty() {
        return quotes.isEmpty();
    }
}
