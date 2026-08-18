package com.forinvest.dashboard.infrastructure.quotes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;

/**
 * Holds the cookie/crumb pair Yahoo's quote endpoint requires, and hands out the crumb.
 *
 * <p>Yahoo stopped serving {@code /v7/finance/quote} to anonymous callers: a bare request is
 * answered with HTTP 401 {@code Unauthorized}. Access is regained by visiting a Yahoo host to be
 * issued a session cookie, then exchanging that cookie for a short opaque "crumb" which every
 * quote request must carry. The two are a pair — a crumb requested without the cookie comes back
 * as {@code Invalid Cookie}, so both halves are acquired together and thrown away together.
 *
 * <p>The pair is cached because it stays valid for many requests; re-fetching it per quote would
 * turn one upstream call into three. It is refreshed only when the quote endpoint rejects it,
 * which is what {@link #invalidate()} is for.
 */
final class YahooCrumbSession {

    private static final Logger LOG = LoggerFactory.getLogger(YahooCrumbSession.class);

    /**
     * A crumb is a short opaque token. Anything longer is not a crumb but an error document being
     * returned with a 200, which Yahoo does when the cookie is missing.
     */
    private static final int MAX_CRUMB_LENGTH = 64;

    private final HttpClient httpClient;
    private final YahooFinanceProperties properties;

    /**
     * Guarded by {@code this}. Reads and writes both happen under the lock so that a refresh
     * triggered by one thread's 401 cannot be interleaved with another thread's acquisition — the
     * cookie jar behind it is shared, and two concurrent handshakes would leave the crumb bound to
     * whichever cookie happened to land last.
     */
    private String crumb;

    YahooCrumbSession(HttpClient httpClient, YahooFinanceProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /** The cached crumb, performing the cookie handshake first if there is not one yet. */
    synchronized String crumb() {
        if (crumb == null) {
            crumb = acquire();
        }
        return crumb;
    }

    /** Drops the cached pair so the next {@link #crumb()} performs a fresh handshake. */
    synchronized void invalidate() {
        crumb = null;
    }

    private String acquire() {
        // The cookie response is deliberately ignored: this host answers 404, and it is the
        // Set-Cookie header on that 404 that matters. The client's CookieHandler stores it.
        send(properties.cookieUrl());

        HttpResponse<String> response = send(properties.crumbUrl());
        String body = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() != 200 || body.isEmpty() || body.length() > MAX_CRUMB_LENGTH) {
            LOG.warn("Yahoo Finance refused to issue a crumb (HTTP {})", response.statusCode());
            throw new StockQuoteUnavailableException("The market data provider refused to authorise this client");
        }
        return body;
    }

    private HttpResponse<String> send(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", properties.userAgent())
                .header("Accept", "*/*")
                .timeout(Duration.ofMillis(properties.connectionTimeoutMillis()))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new StockQuoteUnavailableException("The market data provider could not be reached", failure);
        } catch (InterruptedException interruption) {
            // Restore the flag so the caller's own cancellation still works; the shutting-down
            // request itself is simply reported as unavailable.
            Thread.currentThread().interrupt();
            throw new StockQuoteUnavailableException("The market data request was interrupted", interruption);
        }
    }
}
