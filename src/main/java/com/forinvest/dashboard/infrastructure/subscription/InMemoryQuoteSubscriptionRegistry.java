package com.forinvest.dashboard.infrastructure.subscription;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.QuoteSubscriptions;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;

/**
 * Keeps subscriptions in memory, beside the connections that own them.
 *
 * <p>A subscription is worth exactly as much as the connection it belongs to: when the process
 * stops, every client has been disconnected anyway and has to re-subscribe when it reconnects.
 * Persisting that would store rows nobody could ever be delivered to.
 *
 * <p>The map is concurrent because the two sides of the feed run on different threads: clients
 * subscribe and disconnect on container threads while the broadcast tick reads the whole set on the
 * scheduler thread.
 *
 * <p>This is also the one class that would change to run more than one instance of the application:
 * a shared implementation of the port would let any instance broadcast to any client. Nothing above
 * the port would notice.
 */
@Component
class InMemoryQuoteSubscriptionRegistry implements QuoteSubscriptionRegistry {

    private final Map<SubscriberId, QuoteSubscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void save(QuoteSubscription subscription) {
        // put, not merge: the newest message defines what the client is watching.
        subscriptions.put(subscription.subscriber(), subscription);
    }

    @Override
    public void remove(SubscriberId subscriber) {
        subscriptions.remove(subscriber);
    }

    @Override
    public QuoteSubscriptions current() {
        // Copies out of the live map, so a tick publishes against a set that cannot change while it
        // is being iterated.
        return QuoteSubscriptions.of(subscriptions.values());
    }
}
