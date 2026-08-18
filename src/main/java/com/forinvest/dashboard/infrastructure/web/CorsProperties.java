package com.forinvest.dashboard.infrastructure.web;

import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which browsers may call the REST API, and with what.
 *
 * <p>Origins are matched as patterns rather than literal strings, exactly as the live feed matches
 * them. A literal {@code *} and credentials are illegal together — the browser rejects the pair —
 * whereas a pattern echoes the caller's origin back, so the open default keeps working if a
 * deployment ever turns credentials on.
 *
 * <p>{@code exposedHeaders} defaults to {@code Location} because creating a portfolio answers 201
 * with that header and nothing else; a browser cannot read it unless it is exposed, which would
 * leave cross-origin clients unable to find the resource they just created.
 *
 * @param pathPattern the paths CORS applies to
 * @param allowedOrigins comma-separated origin patterns allowed to call the API
 * @param allowedMethods comma-separated HTTP methods a browser may use
 * @param allowedHeaders comma-separated request headers a browser may send
 * @param exposedHeaders comma-separated response headers a browser may read
 * @param allowCredentials whether cookies and authorisation headers may be sent
 * @param maxAgeSeconds how long a browser may cache the preflight answer
 */
@ConfigurationProperties(prefix = "dashboard.web.cors")
record CorsProperties(
        String pathPattern,
        String allowedOrigins,
        String allowedMethods,
        String allowedHeaders,
        String exposedHeaders,
        Boolean allowCredentials,
        Long maxAgeSeconds) {

    private static final String DEFAULT_PATH_PATTERN = "/api/v1/**";
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";
    private static final String DEFAULT_ALLOWED_METHODS = "GET,POST,PATCH,DELETE,OPTIONS";
    private static final String DEFAULT_ALLOWED_HEADERS = "*";
    private static final String DEFAULT_EXPOSED_HEADERS = "Location";
    private static final boolean DEFAULT_ALLOW_CREDENTIALS = false;
    private static final long DEFAULT_MAX_AGE_SECONDS = 3_600;

    CorsProperties {
        pathPattern = orDefault(pathPattern, DEFAULT_PATH_PATTERN);
        allowedOrigins = orDefault(allowedOrigins, DEFAULT_ALLOWED_ORIGINS);
        allowedMethods = orDefault(allowedMethods, DEFAULT_ALLOWED_METHODS);
        allowedHeaders = orDefault(allowedHeaders, DEFAULT_ALLOWED_HEADERS);
        exposedHeaders = orDefault(exposedHeaders, DEFAULT_EXPOSED_HEADERS);
        allowCredentials = orDefault(allowCredentials, DEFAULT_ALLOW_CREDENTIALS);
        maxAgeSeconds = nonNegativeOrDefault(maxAgeSeconds, DEFAULT_MAX_AGE_SECONDS);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean orDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static long nonNegativeOrDefault(Long value, long fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    /** The origin patterns as the CORS registration wants them. */
    String[] originPatterns() {
        return split(allowedOrigins);
    }

    /** The methods as the CORS registration wants them. */
    String[] methods() {
        return split(allowedMethods);
    }

    /** The request headers as the CORS registration wants them. */
    String[] requestHeaders() {
        return split(allowedHeaders);
    }

    /** The response headers a browser is allowed to read. */
    String[] responseHeaders() {
        return split(exposedHeaders);
    }

    private static String[] split(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toArray(String[]::new);
    }
}
