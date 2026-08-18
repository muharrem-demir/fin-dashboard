package com.forinvest.dashboard.domain.model;

import java.util.Objects;

/**
 * Identifies one live consumer of the quote stream.
 *
 * <p>Opaque on purpose. The domain knows that quotes are pushed to somebody; it does not know that
 * somebody is a WebSocket session, which is what lets the streaming rules be tested with plain
 * JUnit and lets the transport be replaced without touching them.
 *
 * <p>The transport assigns the value, never a client, so a blank one is a programming error rather
 * than a {@code DomainException}: no request a user can make could produce it.
 */
public record SubscriberId(String value) {

    public SubscriberId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Subscriber id must not be blank");
        }
    }

    public static SubscriberId of(String value) {
        return new SubscriberId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
