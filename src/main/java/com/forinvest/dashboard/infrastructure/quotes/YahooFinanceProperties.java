package com.forinvest.dashboard.infrastructure.quotes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the Yahoo Finance quote provider.
 *
 * <p>The endpoints are configurable so a test can point the adapter at a local stub server, and so
 * a deployment can move to {@code query2} if {@code query1} starts refusing it. They are not
 * expected to be set in normal operation.
 *
 * @param connectionTimeoutMillis how long to wait on the upstream call before giving up
 * @param quoteUrl the batch quote endpoint
 * @param crumbUrl the endpoint issuing the crumb token that {@code quoteUrl} demands
 * @param cookieUrl the endpoint visited purely to be handed the session cookie the crumb is bound
 *     to
 * @param userAgent sent on every upstream call; Yahoo refuses requests without a browser-like one
 */
@ConfigurationProperties(prefix = "dashboard.quotes.yahoo")
public record YahooFinanceProperties(
        Integer connectionTimeoutMillis, String quoteUrl, String crumbUrl, String cookieUrl, String userAgent) {

    private static final int DEFAULT_TIMEOUT_MILLIS = 10_000;

    private static final String DEFAULT_QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote";

    private static final String DEFAULT_CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";

    private static final String DEFAULT_COOKIE_URL = "https://fc.yahoo.com";

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    public YahooFinanceProperties {
        if (connectionTimeoutMillis == null || connectionTimeoutMillis <= 0) {
            connectionTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        }
        quoteUrl = defaulted(quoteUrl, DEFAULT_QUOTE_URL);
        crumbUrl = defaulted(crumbUrl, DEFAULT_CRUMB_URL);
        cookieUrl = defaulted(cookieUrl, DEFAULT_COOKIE_URL);
        userAgent = defaulted(userAgent, DEFAULT_USER_AGENT);
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
