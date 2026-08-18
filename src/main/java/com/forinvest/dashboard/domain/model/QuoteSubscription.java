package com.forinvest.dashboard.domain.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;

/**
 * What one subscriber is currently watching.
 *
 * <p>Immutable, like every other domain value: changing the symbols a client follows produces a new
 * subscription that replaces the old one, so a set of tickers is never observed half-updated while
 * a broadcast is in flight.
 *
 * <p>Ticker normalisation is what makes {@code aapl} and {@code AAPL} one subscribed symbol rather
 * than two, exactly as it makes them one holding in a portfolio.
 */
public record QuoteSubscription(SubscriberId subscriber, List<Ticker> tickers) {

    /**
     * Upper bound on one subscription.
     *
     * <p>The same bound a REST batch gets, for the same reason: every subscription becomes part of
     * one upstream call on every tick, so an unbounded list would let a single client hand the
     * quote provider an arbitrarily large query on our behalf — several times a minute, not once.
     */
    public static final int MAX_TICKERS = 50;

    public QuoteSubscription {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        Objects.requireNonNull(tickers, "tickers must not be null");
        // LinkedHashSet: duplicates collapse, and the client's ordering is the order its updates
        // come back in.
        tickers = List.copyOf(new LinkedHashSet<>(tickers));
        if (tickers.isEmpty()) {
            throw InvalidQuoteRequestException.empty();
        }
        if (tickers.size() > MAX_TICKERS) {
            throw InvalidQuoteRequestException.tooMany(tickers.size(), MAX_TICKERS);
        }
    }

    /**
     * Builds a subscription from the raw symbols a client sent.
     *
     * <p>Parsing happens here rather than at the transport, so a malformed symbol is a domain error
     * that the client is told about — not something the edge quietly normalises away.
     *
     * @throws com.forinvest.dashboard.domain.exception.InvalidTickerException if a symbol is not a
     *     valid ticker
     */
    public static QuoteSubscription of(SubscriberId subscriber, List<String> symbols) {
        Objects.requireNonNull(symbols, "symbols must not be null");
        return new QuoteSubscription(
                subscriber, symbols.stream().map(Ticker::of).toList());
    }

    /**
     * This subscriber's share of a batch that was fetched for everybody.
     *
     * <p>One upstream call serves every open connection; each subscriber then sees only the symbols
     * it asked for, in the order it asked for them, with its own missing symbols reported as
     * unresolved. Reconciling against the whole batch is what keeps "no data for this ticker"
     * distinguishable from "somebody else asked for it".
     */
    public StockQuoteLookup select(StockQuoteLookup batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        return StockQuoteLookup.reconcile(tickers, batch.quotes());
    }
}
