package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.SubscribeToQuotesCommand;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;

/**
 * Registers, or changes, what one client is watching.
 *
 * <p>There is one path for both: saving replaces whatever the subscriber had before, so a client
 * changing its symbols mid-stream is the same operation as subscribing for the first time. Nothing
 * is fetched here — the next tick picks the change up.
 */
public final class SubscribeToQuotesUseCase {

    private final QuoteSubscriptionRegistry subscriptionRegistry;

    public SubscribeToQuotesUseCase(QuoteSubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry);
    }

    /**
     * @return the stored subscription, with its symbols normalised and de-duplicated, so the
     *     transport can tell the client exactly what it is now watching
     * @throws com.forinvest.dashboard.domain.exception.InvalidTickerException if a symbol is not a
     *     valid ticker
     * @throws com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException if no symbols
     *     were given, or more than a subscription may hold
     */
    public QuoteSubscription execute(SubscribeToQuotesCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        QuoteSubscription subscription =
                QuoteSubscription.of(SubscriberId.of(command.subscriberId()), command.tickers());
        subscriptionRegistry.save(subscription);
        return subscription;
    }
}
