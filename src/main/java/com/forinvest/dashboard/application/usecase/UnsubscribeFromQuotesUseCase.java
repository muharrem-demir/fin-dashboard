package com.forinvest.dashboard.application.usecase;

import java.util.Objects;

import com.forinvest.dashboard.application.command.UnsubscribeFromQuotesCommand;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;

/**
 * Stops a client's updates, whether it asked or its connection simply went away.
 *
 * <p>Idempotent on purpose: a disconnect races with an explicit unsubscribe, and neither order may
 * fail. That is the opposite choice from removing a stock from a portfolio, where "you do not hold
 * that" is information the user wants — here there is nobody left to tell.
 */
public final class UnsubscribeFromQuotesUseCase {

    private final QuoteSubscriptionRegistry subscriptionRegistry;

    public UnsubscribeFromQuotesUseCase(QuoteSubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry);
    }

    public void execute(UnsubscribeFromQuotesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        subscriptionRegistry.remove(SubscriberId.of(command.subscriberId()));
    }
}
