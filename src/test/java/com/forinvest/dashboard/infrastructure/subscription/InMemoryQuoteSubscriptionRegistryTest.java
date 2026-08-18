package com.forinvest.dashboard.infrastructure.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.model.Ticker;

class InMemoryQuoteSubscriptionRegistryTest {

    private static final SubscriberId ONE = SubscriberId.of("session-1");
    private static final SubscriberId TWO = SubscriberId.of("session-2");

    private InMemoryQuoteSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryQuoteSubscriptionRegistry();
    }

    private static QuoteSubscription subscription(SubscriberId subscriber, String... symbols) {
        return QuoteSubscription.of(subscriber, List.of(symbols));
    }

    @Test
    @DisplayName("a second subscription from the same client replaces the first")
    void savingReplacesTheSubscription() {
        registry.save(subscription(ONE, "AAPL"));
        registry.save(subscription(ONE, "TSLA", "NVDA"));

        assertThat(registry.current().size()).isEqualTo(1);
        assertThat(registry.current().tickers()).containsExactly(Ticker.of("TSLA"), Ticker.of("NVDA"));
    }

    @Test
    @DisplayName("keeps every client's watchlist separate")
    void keepsSubscribersApart() {
        registry.save(subscription(ONE, "AAPL"));
        registry.save(subscription(TWO, "TSLA"));

        assertThat(registry.current().subscriptions())
                .extracting(QuoteSubscription::subscriber)
                .containsExactlyInAnyOrder(ONE, TWO);
    }

    @Test
    @DisplayName("removing an unknown subscriber is a no-op, because a disconnect may arrive twice")
    void removalIsIdempotent() {
        registry.save(subscription(ONE, "AAPL"));

        assertThatCode(() -> {
                    registry.remove(ONE);
                    registry.remove(ONE);
                    registry.remove(TWO);
                })
                .doesNotThrowAnyException();
        assertThat(registry.current().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("hands out a snapshot, so a tick is not disturbed by clients coming and going")
    void currentIsASnapshot() {
        registry.save(subscription(ONE, "AAPL"));

        var snapshot = registry.current();
        registry.save(subscription(TWO, "TSLA"));

        assertThat(snapshot.size()).isEqualTo(1);
        assertThat(registry.current().size()).isEqualTo(2);
    }
}
