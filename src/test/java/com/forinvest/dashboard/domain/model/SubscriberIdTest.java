package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriberIdTest {

    @Test
    @DisplayName("keeps the value the transport assigned, verbatim")
    void keepsItsValue() {
        SubscriberId subscriber = SubscriberId.of("session-42");

        assertThat(subscriber.value()).isEqualTo("session-42");
        assertThat(subscriber).hasToString("session-42");
    }

    @Test
    @DisplayName("two ids with the same value are the same subscriber")
    void hasValueEquality() {
        assertThat(SubscriberId.of("session-42")).isEqualTo(new SubscriberId("session-42"));
    }

    @Test
    @DisplayName("rejects a blank id, which could only ever be a bug in the transport")
    void rejectsBlank() {
        assertThatThrownBy(() -> SubscriberId.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriberId.of(null)).isInstanceOf(NullPointerException.class);
    }
}
