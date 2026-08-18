package com.forinvest.dashboard.infrastructure.websocket.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A message from a client: the only thing it can say over the socket.
 *
 * <p>Deliberately liberal — unknown fields are ignored and a missing list is treated as an empty
 * one, so a client sending a little too much gets an answer rather than a parse failure. What it
 * cannot do is send nonsense that reaches the domain: an empty or invalid symbol list is rejected
 * there and reported back as an error frame.
 *
 * <pre>{@code
 * {"action": "subscribe", "tickers": ["AAPL", "MSFT"]}
 * {"action": "unsubscribe"}
 * }</pre>
 *
 * @param action {@code subscribe} or {@code unsubscribe}, case-insensitive
 * @param tickers the symbols to watch; replaces whatever was watched before
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteStreamCommand(String action, List<String> tickers) {

    public List<String> tickersOrEmpty() {
        return tickers == null ? List.of() : tickers;
    }
}
