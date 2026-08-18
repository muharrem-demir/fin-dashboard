package com.forinvest.dashboard.domain.port;

import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.QuoteSubscriptions;
import com.forinvest.dashboard.domain.model.SubscriberId;

/**
 * The domain's view of who is listening for quote updates right now.
 *
 * <p>An outbound port. The shipped adapter keeps subscriptions in memory beside the connections
 * they belong to, which is correct for a single instance; running several would want a shared
 * implementation, and this interface is the seam where that swap happens.
 */
public interface QuoteSubscriptionRegistry {

    /**
     * Records what a subscriber wants, replacing anything it was watching before.
     *
     * <p>Replacement rather than merge is the whole of "a client may change its symbols at any
     * time": the newest message wins, and there is no separate unsubscribe-then-subscribe dance
     * during which a client would receive updates for symbols it has already dropped.
     */
    void save(QuoteSubscription subscription);

    /** Forgets a subscriber. A no-op when it has nothing registered, so a disconnect is safe. */
    void remove(SubscriberId subscriber);

    /**
     * A snapshot of every live subscription.
     *
     * <p>A snapshot, not a live view: a tick must fetch and publish against a set that cannot
     * change underneath it while clients connect and disconnect.
     */
    QuoteSubscriptions current();
}
