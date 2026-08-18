package com.forinvest.dashboard.infrastructure.websocket.dto;

import java.time.Instant;
import java.util.List;

import com.forinvest.dashboard.infrastructure.web.dto.StockQuoteResponse;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuotesResponse;

/**
 * One tick of quotes for one subscriber.
 *
 * <p>The quotes themselves are the same {@link StockQuoteResponse} the REST endpoint returns: a
 * quote is one concept, and a client that reads {@code GET /stocks/quotes} and the stream should
 * not have to parse two shapes of it. Only the envelope — a type discriminator and the time the
 * tick was published — is specific to streaming.
 */
public record QuoteUpdateMessage(
        String type, Instant timestamp, List<StockQuoteResponse> quotes, List<String> unresolved, int quoteCount) {

    public static final String TYPE = "quotes";

    public static QuoteUpdateMessage of(StockQuotesResponse quotes, Instant timestamp) {
        return new QuoteUpdateMessage(TYPE, timestamp, quotes.quotes(), quotes.unresolved(), quotes.quoteCount());
    }
}
