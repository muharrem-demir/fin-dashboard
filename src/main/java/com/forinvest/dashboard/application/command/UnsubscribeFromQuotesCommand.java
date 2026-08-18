package com.forinvest.dashboard.application.command;

import java.util.Objects;

/**
 * A client asking to stop receiving quote updates, or a connection that has gone away.
 *
 * @param subscriberId the connection to forget
 */
public record UnsubscribeFromQuotesCommand(String subscriberId) {

    public UnsubscribeFromQuotesCommand {
        Objects.requireNonNull(subscriberId, "subscriberId must not be null");
    }
}
