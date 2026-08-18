package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuoteSubscriptionsTest {

    private static QuoteSubscription subscription(String subscriber, String... symbols) {
        return QuoteSubscription.of(SubscriberId.of(subscriber), List.of(symbols));
    }

    @Test
    @DisplayName("asks for every watched symbol exactly once, however much the clients overlap")
    void unionIsDeduplicated() {
        QuoteSubscriptions subscriptions = QuoteSubscriptions.of(List.of(
                subscription("one", "AAPL", "MSFT"),
                subscription("two", "aapl", "TSLA"),
                subscription("three", "AAPL")));

        assertThat(subscriptions.tickers()).containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA"));
        assertThat(subscriptions.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("asks for nothing when nobody is listening")
    void emptyWhenNobodyIsSubscribed() {
        QuoteSubscriptions subscriptions = QuoteSubscriptions.none();

        assertThat(subscriptions.isEmpty()).isTrue();
        assertThat(subscriptions.tickers()).isEmpty();
        assertThat(subscriptions.size()).isZero();
    }

    @Test
    @DisplayName("refuses to hold two subscriptions for one subscriber")
    void rejectsDuplicateSubscribers() {
        List<QuoteSubscription> duplicated = List.of(subscription("one", "AAPL"), subscription("one", "MSFT"));

        assertThatThrownBy(() -> QuoteSubscriptions.of(duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one subscription");
    }

    @Test
    @DisplayName("rejects a null collection")
    void rejectsNull() {
        assertThatThrownBy(() -> QuoteSubscriptions.of(null)).isInstanceOf(NullPointerException.class);
    }
}
