package com.forinvest.dashboard.application.usecase;

import java.util.List;
import java.util.Objects;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.QuoteSubscriptions;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;
import com.forinvest.dashboard.domain.port.QuoteUpdatePublisher;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

/**
 * One tick of the live feed: fetch what everybody is watching, hand each client its own share.
 *
 * <p>Read-only and stateless, like {@link ListStockQuotesUseCase} — nothing is persisted, so there
 * is no repository and no transaction. What it adds is fan-out: the number of upstream calls is
 * driven by how often the feed ticks, never by how many clients are connected.
 *
 * <p>Nothing is fetched when nobody is listening. That is not only an optimisation: it is why an
 * idle application — a test context, a developer machine — never calls the quote provider at all.
 */
public final class BroadcastQuoteUpdatesUseCase {

    private final QuoteSubscriptionRegistry subscriptionRegistry;
    private final StockQuoteProvider stockQuoteProvider;
    private final QuoteUpdatePublisher quoteUpdatePublisher;

    public BroadcastQuoteUpdatesUseCase(
            QuoteSubscriptionRegistry subscriptionRegistry,
            StockQuoteProvider stockQuoteProvider,
            QuoteUpdatePublisher quoteUpdatePublisher) {
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry);
        this.stockQuoteProvider = Objects.requireNonNull(stockQuoteProvider);
        this.quoteUpdatePublisher = Objects.requireNonNull(quoteUpdatePublisher);
    }

    /**
     * Runs one tick.
     *
     * <p>A provider outage is reported to every subscriber and then forgotten: the feed is
     * periodic, so the next tick is the retry. Letting the failure escape would only give the
     * scheduler something to log.
     *
     * @return how many subscribers were published to
     */
    public int execute() {
        QuoteSubscriptions subscriptions = subscriptionRegistry.current();
        if (subscriptions.isEmpty()) {
            return 0;
        }

        List<Ticker> tickers = subscriptions.tickers();
        StockQuoteLookup batch;
        try {
            batch = StockQuoteLookup.reconcile(tickers, stockQuoteProvider.findQuotes(tickers));
        } catch (StockQuoteUnavailableException failure) {
            for (QuoteSubscription subscription : subscriptions.subscriptions()) {
                quoteUpdatePublisher.publishUnavailable(subscription.subscriber(), failure.getMessage());
            }
            return subscriptions.size();
        }

        for (QuoteSubscription subscription : subscriptions.subscriptions()) {
            quoteUpdatePublisher.publishQuotes(subscription.subscriber(), subscription.select(batch));
        }
        return subscriptions.size();
    }
}
