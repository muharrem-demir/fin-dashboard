package com.forinvest.dashboard.domain.port;

import java.util.List;

import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;

/**
 * The domain's view of a market-data source.
 *
 * <p>An outbound port. {@code infrastructure.quotes} supplies the Yahoo Finance adapter; nothing
 * above this interface knows which provider is in use, so replacing it is a one-class change.
 */
public interface StockQuoteProvider {

    /**
     * Fetches quotes for several tickers in a single upstream call.
     *
     * <p>Batching is part of the contract, not an optimisation left to the adapter: quoting twenty
     * tickers must not become twenty round trips.
     *
     * <p>May return fewer quotes than were requested when the provider does not recognise a
     * symbol. Callers reconcile that with
     * {@link com.forinvest.dashboard.domain.model.StockQuoteLookup#reconcile}.
     *
     * @throws com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException if the
     *     provider cannot be reached or refuses the request
     */
    List<StockQuote> findQuotes(List<Ticker> tickers);
}
