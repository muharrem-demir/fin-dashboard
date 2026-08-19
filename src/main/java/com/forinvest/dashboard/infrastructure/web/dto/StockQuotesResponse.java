package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quotes for a batch of tickers.
 *
 * <p>History sits beside the quotes rather than inside each one so that {@link StockQuoteResponse}
 * keeps a single shape everywhere. That record is also the wire format of the live feed, which
 * carries no history and must not grow a field it would always send empty.
 */
@Schema(description = "Quotes for a batch of tickers")
public record StockQuotesResponse(
        @Schema(description = "Quotes, in the order the tickers were requested")
        List<StockQuoteResponse> quotes,

        @Schema(
                description = "Requested tickers the provider had no data for. Present so a client can tell"
                        + " 'no data' apart from 'not asked for'.",
                example = "[\"NOSUCH\"]")
        List<String> unresolved,

        @Schema(description = "Number of quotes returned", example = "2")
        int quoteCount,

        @Schema(
                description = "Recent daily closes, one entry per ticker that had any, in the same order as the"
                        + " quotes. Absent unless `history=true` was requested; empty when it was requested and"
                        + " the provider had no history for any of the tickers.")
        List<PriceHistoryResponse> history) {

    /** The shape returned when history was not asked for: the field is omitted rather than sent empty. */
    public StockQuotesResponse(List<StockQuoteResponse> quotes, List<String> unresolved, int quoteCount) {
        this(quotes, unresolved, quoteCount, null);
    }
}
