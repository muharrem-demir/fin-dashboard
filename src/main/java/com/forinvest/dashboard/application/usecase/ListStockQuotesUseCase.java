package com.forinvest.dashboard.application.usecase;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

/**
 * Fetches quotes for several tickers in one upstream call.
 *
 * <p>Read-only and stateless: nothing is persisted, so there is no repository and no transaction.
 * The use case parses and de-duplicates the symbols, makes exactly one call to the provider, and
 * lets the domain reconcile what came back.
 */
public final class ListStockQuotesUseCase {

    private final StockQuoteProvider stockQuoteProvider;

    public ListStockQuotesUseCase(StockQuoteProvider stockQuoteProvider) {
        this.stockQuoteProvider = Objects.requireNonNull(stockQuoteProvider);
    }

    /**
     * @throws com.forinvest.dashboard.domain.exception.InvalidTickerException if a symbol is not a
     *     valid ticker
     * @throws com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException if the
     *     provider cannot be reached
     */
    public StockQuoteLookup execute(ListStockQuotesQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        // Ticker normalises case, so "aapl" and "AAPL" collapse to one symbol and are not paid for
        // twice upstream. LinkedHashSet keeps the caller's ordering.
        List<Ticker> tickers = new LinkedHashSet<>(
                        query.tickers().stream().map(Ticker::of).toList())
                .stream().toList();

        List<StockQuote> quotes = stockQuoteProvider.findQuotes(tickers);
        return StockQuoteLookup.reconcile(tickers, quotes);
    }
}
