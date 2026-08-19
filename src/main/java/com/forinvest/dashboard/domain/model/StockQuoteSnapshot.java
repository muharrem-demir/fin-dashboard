package com.forinvest.dashboard.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a batch quote request answers with: the quotes, and — when they were asked for — their
 * recent daily closes.
 *
 * <p>History is a component beside the quotes rather than a field on {@link StockQuote} on purpose.
 * A quote is one price at one moment and is published to the live feed several times a minute;
 * history is a much larger, much slower-moving answer that only a caller who asked for it should
 * pay for. Keeping them apart is what lets the streaming path carry {@link StockQuoteLookup} alone
 * and stay exactly as cheap as it was.
 *
 * <p>{@code historyIncluded} is not the same question as "are there any histories". A request that
 * asked for history and got none back is a different answer from one that never asked, and a
 * client that cannot tell them apart has no way to know whether to keep the chart it is holding.
 */
public record StockQuoteSnapshot(StockQuoteLookup quotes, List<PriceHistory> histories, boolean historyIncluded) {

    public StockQuoteSnapshot {
        Objects.requireNonNull(quotes, "quotes must not be null");
        Objects.requireNonNull(histories, "histories must not be null");
        histories = List.copyOf(histories);
        if (!historyIncluded && !histories.isEmpty()) {
            throw new IllegalArgumentException("A snapshot without history must not carry histories");
        }
    }

    /** The answer to a request that did not ask for history. */
    public static StockQuoteSnapshot withoutHistory(StockQuoteLookup quotes) {
        return new StockQuoteSnapshot(quotes, List.of(), false);
    }

    /**
     * Pairs a lookup with the histories that came back for it.
     *
     * <p>Histories follow the order of the lookup, so a client reads quotes and histories in the
     * same sequence it asked for its tickers. An empty history is dropped rather than reported as a
     * ticker with no points: "the provider knows nothing about this symbol" is already said by
     * {@link StockQuoteLookup#unresolved()}, and saying it twice in two shapes only gives a client
     * two things to check.
     */
    public static StockQuoteSnapshot of(StockQuoteLookup quotes, List<PriceHistory> histories) {
        Objects.requireNonNull(quotes, "quotes must not be null");
        Objects.requireNonNull(histories, "histories must not be null");

        Map<Ticker, PriceHistory> byTicker = new LinkedHashMap<>();
        for (PriceHistory history : histories) {
            if (!history.isEmpty()) {
                byTicker.put(history.ticker(), history);
            }
        }

        List<PriceHistory> ordered = new ArrayList<>(byTicker.size());
        for (Ticker ticker : requestedOrder(quotes)) {
            PriceHistory history = byTicker.get(ticker);
            if (history != null) {
                ordered.add(history);
            }
        }
        return new StockQuoteSnapshot(quotes, ordered, true);
    }

    /** Quoted tickers first, then the ones nothing came back for — the order the lookup reports them in. */
    private static List<Ticker> requestedOrder(StockQuoteLookup quotes) {
        List<Ticker> tickers =
                new ArrayList<>(quotes.quotes().size() + quotes.unresolved().size());
        quotes.quotes().forEach(quote -> tickers.add(quote.ticker()));
        tickers.addAll(quotes.unresolved());
        return tickers;
    }
}
