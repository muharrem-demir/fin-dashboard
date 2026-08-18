package com.forinvest.dashboard.domain.port;

import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.SubscriberId;

/**
 * The domain's view of pushing an update to one subscriber.
 *
 * <p>An outbound port, declared in domain types so nothing above it knows the delivery happens over
 * a WebSocket. The adapter is expected to swallow and log a delivery failure rather than throw:
 * one client whose connection has gone away must not cost every other client its update.
 */
public interface QuoteUpdatePublisher {

    /** Pushes the quotes a subscriber asked for. */
    void publishQuotes(SubscriberId subscriber, StockQuoteLookup quotes);

    /**
     * Tells a subscriber that this tick produced no data because the provider could not be reached.
     *
     * <p>Reported rather than hidden: a dashboard showing prices that quietly stopped updating is
     * worse than one that says the feed is down.
     */
    void publishUnavailable(SubscriberId subscriber, String reason);
}
