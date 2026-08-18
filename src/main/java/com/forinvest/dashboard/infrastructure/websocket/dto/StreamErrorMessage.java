package com.forinvest.dashboard.infrastructure.websocket.dto;

import java.time.Instant;

/**
 * Something went wrong, and the connection stays open anyway.
 *
 * <p>Two kinds, because they mean opposite things to a client. {@code error} is the client's own
 * mistake — a symbol that is not a ticker, an action nobody understands — and the previous
 * subscription is untouched. {@code unavailable} is the quote provider failing, the streaming
 * counterpart of the 502 the REST endpoint returns: the request was fine, the dependency was not,
 * and the next tick is the retry.
 */
public record StreamErrorMessage(String type, String message, Instant timestamp) {

    public static final String ERROR = "error";
    public static final String UNAVAILABLE = "unavailable";

    public static StreamErrorMessage error(String message, Instant timestamp) {
        return new StreamErrorMessage(ERROR, message, timestamp);
    }

    public static StreamErrorMessage unavailable(String reason, Instant timestamp) {
        return new StreamErrorMessage(UNAVAILABLE, reason, timestamp);
    }
}
