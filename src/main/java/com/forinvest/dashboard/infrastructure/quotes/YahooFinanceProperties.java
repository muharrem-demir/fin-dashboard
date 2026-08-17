package com.forinvest.dashboard.infrastructure.quotes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the Yahoo Finance quote provider.
 *
 * @param connectionTimeoutMillis how long to wait on the upstream call before giving up
 */
@ConfigurationProperties(prefix = "dashboard.quotes.yahoo")
public record YahooFinanceProperties(Integer connectionTimeoutMillis) {

    private static final int DEFAULT_TIMEOUT_MILLIS = 10_000;

    public YahooFinanceProperties {
        if (connectionTimeoutMillis == null || connectionTimeoutMillis <= 0) {
            connectionTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        }
    }
}
