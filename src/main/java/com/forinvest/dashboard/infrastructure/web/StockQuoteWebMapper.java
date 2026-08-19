package com.forinvest.dashboard.infrastructure.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.PricePoint;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.StockQuoteSnapshot;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.infrastructure.web.dto.PriceHistoryResponse;
import com.forinvest.dashboard.infrastructure.web.dto.PricePointResponse;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuoteResponse;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuotesResponse;

/**
 * Turns a domain quote lookup into the API's response shape.
 *
 * <p>The percent change is read from the domain, never recomputed here — there must be exactly one
 * definition of that formula in the system.
 *
 * <p>Public because the WebSocket feed pushes the same quotes this endpoint returns. One mapper
 * means a quote has one JSON shape whether a client polls for it or is pushed it. The feed uses the
 * {@link StockQuoteLookup} overload and is unaffected by history: the quotes it sends are mapped by
 * the same code and come out byte for byte as they did before history existed.
 */
@Component
public class StockQuoteWebMapper {

    /** Quotes alone. This is what the live feed maps with. */
    public StockQuotesResponse toResponse(StockQuoteLookup lookup) {
        return new StockQuotesResponse(
                quotes(lookup), unresolved(lookup), lookup.quotes().size());
    }

    /** Quotes, plus history when the request asked for it. */
    public StockQuotesResponse toResponse(StockQuoteSnapshot snapshot) {
        StockQuoteLookup lookup = snapshot.quotes();
        if (!snapshot.historyIncluded()) {
            return toResponse(lookup);
        }
        return new StockQuotesResponse(
                quotes(lookup),
                unresolved(lookup),
                lookup.quotes().size(),
                snapshot.histories().stream()
                        .map(StockQuoteWebMapper::toHistoryResponse)
                        .toList());
    }

    private static List<StockQuoteResponse> quotes(StockQuoteLookup lookup) {
        return lookup.quotes().stream()
                .map(StockQuoteWebMapper::toQuoteResponse)
                .toList();
    }

    private static List<String> unresolved(StockQuoteLookup lookup) {
        return lookup.unresolved().stream().map(Ticker::symbol).toList();
    }

    private static StockQuoteResponse toQuoteResponse(StockQuote quote) {
        return new StockQuoteResponse(
                quote.ticker().symbol(),
                quote.price(),
                quote.previousClose(),
                quote.percentChange().orElse(null));
    }

    private static PriceHistoryResponse toHistoryResponse(PriceHistory history) {
        return new PriceHistoryResponse(
                history.ticker().symbol(),
                history.window().days(),
                history.points().stream()
                        .map(StockQuoteWebMapper::toPointResponse)
                        .toList());
    }

    private static PricePointResponse toPointResponse(PricePoint point) {
        return new PricePointResponse(point.date(), point.close());
    }
}
