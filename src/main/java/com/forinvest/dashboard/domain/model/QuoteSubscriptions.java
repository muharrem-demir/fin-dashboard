package com.forinvest.dashboard.domain.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Everyone currently listening for quote updates, and therefore everything that has to be fetched.
 *
 * <p>The rule that lives here is the one that makes streaming to many clients affordable: a tick
 * asks the provider for the <em>union</em> of every subscribed symbol, once, no matter how many
 * connections are open or how much their watchlists overlap. Ten clients all watching AAPL cost one
 * symbol, not ten.
 */
public record QuoteSubscriptions(List<QuoteSubscription> subscriptions) {

    public QuoteSubscriptions {
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        subscriptions = List.copyOf(subscriptions);
        long distinctSubscribers = subscriptions.stream()
                .map(QuoteSubscription::subscriber)
                .distinct()
                .count();
        if (distinctSubscribers != subscriptions.size()) {
            // A subscriber has exactly one subscription: changing symbols replaces it. Two entries
            // for one subscriber would mean somebody is owed two different updates per tick.
            throw new IllegalArgumentException("A subscriber may hold at most one subscription");
        }
    }

    public static QuoteSubscriptions none() {
        return new QuoteSubscriptions(List.of());
    }

    public static QuoteSubscriptions of(Collection<QuoteSubscription> subscriptions) {
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        return new QuoteSubscriptions(List.copyOf(subscriptions));
    }

    /** Every symbol anybody is watching, each one exactly once. */
    public List<Ticker> tickers() {
        return List.copyOf(subscriptions.stream()
                .flatMap(subscription -> subscription.tickers().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    public int size() {
        return subscriptions.size();
    }
}
