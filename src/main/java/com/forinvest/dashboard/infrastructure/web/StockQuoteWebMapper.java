package com.forinvest.dashboard.infrastructure.web;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuoteResponse;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuotesResponse;

/**
 * Turns a domain quote lookup into the API's response shape.
 *
 * <p>The percent change is read from the domain, never recomputed here — there must be exactly one
 * definition of that formula in the system.
 *
 * <p>Public because the WebSocket feed pushes the same quotes this endpoint returns. One mapper
 * means a quote has one JSON shape whether a client polls for it or is pushed it.
 */
@Component
public class StockQuoteWebMapper {

    public StockQuotesResponse toResponse(StockQuoteLookup lookup) {
        return new StockQuotesResponse(
                lookup.quotes().stream()
                        .map(StockQuoteWebMapper::toQuoteResponse)
                        .toList(),
                lookup.unresolved().stream().map(Ticker::symbol).toList(),
                lookup.quotes().size());
    }

    private static StockQuoteResponse toQuoteResponse(StockQuote quote) {
        return new StockQuoteResponse(
                quote.ticker().symbol(),
                quote.price(),
                quote.previousClose(),
                quote.percentChange().orElse(null));
    }
}
