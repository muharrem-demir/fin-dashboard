package com.forinvest.dashboard.infrastructure.websocket.dto;

import java.util.List;

/**
 * Acknowledges what a client is now watching.
 *
 * <p>The symbols are echoed back as the domain normalised them — upper-cased and de-duplicated —
 * so a client that sent {@code ["aapl", "AAPL"]} can see it is watching one symbol, not two.
 */
public record SubscriptionMessage(String type, List<String> tickers) {

    public static final String SUBSCRIBED = "subscribed";
    public static final String UNSUBSCRIBED = "unsubscribed";

    public static SubscriptionMessage subscribed(List<String> tickers) {
        return new SubscriptionMessage(SUBSCRIBED, List.copyOf(tickers));
    }

    public static SubscriptionMessage unsubscribed() {
        return new SubscriptionMessage(UNSUBSCRIBED, List.of());
    }
}
