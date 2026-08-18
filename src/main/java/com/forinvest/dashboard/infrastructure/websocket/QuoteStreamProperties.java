package com.forinvest.dashboard.infrastructure.websocket;

import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the live quote feed.
 *
 * <p>The tick interval is also read straight from the environment by the scheduler: an annotation
 * needs a literal property placeholder, so {@code QuoteBroadcastScheduler} resolves
 * {@code interval-millis} itself and this record carries the same value for everything else that
 * wants to know it — the greeting sent to a new client, and the line logged at startup.
 *
 * @param path the endpoint clients connect to
 * @param intervalMillis how often quotes are fetched and pushed
 * @param allowedOrigins comma-separated origin patterns allowed to open a connection
 * @param sendTimeLimitMillis how long a single send may take before the connection is closed
 * @param sendBufferSizeBytes how much undelivered data may queue for one slow client
 */
@ConfigurationProperties(prefix = "dashboard.quotes.stream")
public record QuoteStreamProperties(
        String path,
        Long intervalMillis,
        String allowedOrigins,
        Integer sendTimeLimitMillis,
        Integer sendBufferSizeBytes) {

    private static final String DEFAULT_PATH = "/ws/quotes";
    private static final long DEFAULT_INTERVAL_MILLIS = 3_000;
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";
    private static final int DEFAULT_SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int DEFAULT_SEND_BUFFER_SIZE_BYTES = 512 * 1024;

    public QuoteStreamProperties {
        path = orDefault(path, DEFAULT_PATH);
        intervalMillis = positiveOrDefault(intervalMillis, DEFAULT_INTERVAL_MILLIS);
        allowedOrigins = orDefault(allowedOrigins, DEFAULT_ALLOWED_ORIGINS);
        sendTimeLimitMillis = positiveOrDefault(sendTimeLimitMillis, DEFAULT_SEND_TIME_LIMIT_MILLIS);
        sendBufferSizeBytes = positiveOrDefault(sendBufferSizeBytes, DEFAULT_SEND_BUFFER_SIZE_BYTES);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long positiveOrDefault(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    /** The origin patterns as the WebSocket registration wants them. */
    public String[] originPatterns() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
