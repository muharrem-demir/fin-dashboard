package com.forinvest.dashboard.application.usecase;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.StockQuoteSnapshot;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

/**
 * Fetches quotes for several tickers in one upstream call, and their history when asked to.
 *
 * <p>Read-only and stateless: nothing is persisted, so there is no repository and no transaction.
 * The use case parses and de-duplicates the symbols, makes exactly one call to the quote provider,
 * and lets the domain reconcile what came back.
 *
 * <p>History is fetched only when the query asks for it, and always for the same de-duplicated
 * tickers the quotes were fetched for. The window comes from configuration and is fixed for the
 * life of the application, so how far back a history reaches is an operator's decision rather than
 * a caller's.
 */
public final class ListStockQuotesUseCase {

    private final StockQuoteProvider stockQuoteProvider;
    private final StockPriceHistoryProvider stockPriceHistoryProvider;
    private final HistoryWindow historyWindow;

    public ListStockQuotesUseCase(
            StockQuoteProvider stockQuoteProvider,
            StockPriceHistoryProvider stockPriceHistoryProvider,
            HistoryWindow historyWindow) {
        this.stockQuoteProvider = Objects.requireNonNull(stockQuoteProvider);
        this.stockPriceHistoryProvider = Objects.requireNonNull(stockPriceHistoryProvider);
        this.historyWindow = Objects.requireNonNull(historyWindow);
    }

    /**
     * @throws com.forinvest.dashboard.domain.exception.InvalidTickerException if a symbol is not a
     *     valid ticker
     * @throws com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException if either
     *     provider cannot be reached
     */
    public StockQuoteSnapshot execute(ListStockQuotesQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        // Ticker normalises case, so "aapl" and "AAPL" collapse to one symbol and are not paid for
        // twice upstream. LinkedHashSet keeps the caller's ordering.
        List<Ticker> tickers = new LinkedHashSet<>(
                        query.tickers().stream().map(Ticker::of).toList())
                .stream().toList();

        List<StockQuote> quotes = stockQuoteProvider.findQuotes(tickers);
        StockQuoteLookup lookup = StockQuoteLookup.reconcile(tickers, quotes);

        if (!query.includeHistory()) {
            return StockQuoteSnapshot.withoutHistory(lookup);
        }
        return StockQuoteSnapshot.of(lookup, stockPriceHistoryProvider.findHistories(tickers, historyWindow));
    }
}
