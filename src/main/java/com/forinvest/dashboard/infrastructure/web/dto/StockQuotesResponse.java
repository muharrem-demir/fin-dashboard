package com.forinvest.dashboard.infrastructure.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

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
        int quoteCount) {}
