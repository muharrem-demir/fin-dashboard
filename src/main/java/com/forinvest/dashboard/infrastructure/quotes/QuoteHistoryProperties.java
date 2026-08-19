package com.forinvest.dashboard.infrastructure.quotes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the price-history provider.
 *
 * <p>{@code days} is the one setting an operator is expected to touch: it decides how much history
 * {@code GET /api/v1/stocks/quotes?history=true} answers with, for every caller. It is deliberately
 * not a request parameter — history costs one upstream call per ticker, and how much of it this
 * deployment is willing to buy is an operator's decision.
 *
 * <p>The endpoint is configurable for the same reason the quote endpoint is: so a test can point
 * the adapter at a local stub, and so a deployment can move hosts. It is not expected to be set in
 * normal operation.
 *
 * @param days how many trading days of closes to report; see
 *     {@link com.forinvest.dashboard.domain.model.HistoryWindow} for what a day means here
 * @param chartUrl the daily-candles endpoint, with the symbol appended as a path segment
 */
@ConfigurationProperties(prefix = "dashboard.quotes.history")
public record QuoteHistoryProperties(Integer days, String chartUrl) {

    /** Five trading days: a working week of context beside the current price. */
    public static final int DEFAULT_DAYS = 5;

    private static final String DEFAULT_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart";

    public QuoteHistoryProperties {
        if (days == null) {
            days = DEFAULT_DAYS;
        }
        if (chartUrl == null || chartUrl.isBlank()) {
            chartUrl = DEFAULT_CHART_URL;
        }
    }
}
