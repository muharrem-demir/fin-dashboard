package com.forinvest.dashboard.domain.port;

import java.util.List;

import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.Ticker;

/**
 * The domain's view of a source of past daily closes.
 *
 * <p>A port of its own rather than another method on {@link StockQuoteProvider}, because the two
 * make different promises. A quote provider must answer a whole batch in one upstream call, and
 * the live feed depends on that. History is not batchable at every provider, so this port promises
 * only that it will be asked once per request — never once per subscriber, and never on the
 * streaming path at all.
 *
 * <p>Separating them also means the streaming path keeps talking to exactly the interface it
 * always did: adding history to the REST endpoint cannot change what one tick of the feed costs.
 */
public interface StockPriceHistoryProvider {

    /**
     * Fetches recent daily closes for each ticker.
     *
     * <p>May return fewer histories than were requested, or a shorter history than the window asks
     * for: a symbol the provider does not recognise, and one that has only traded for two days,
     * are both answered with what exists rather than with padding. Callers reconcile that with
     * {@link com.forinvest.dashboard.domain.model.StockQuoteSnapshot#of}.
     *
     * @throws com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException if the
     *     provider cannot be reached or refuses the request
     */
    List<PriceHistory> findHistories(List<Ticker> tickers, HistoryWindow window);
}
