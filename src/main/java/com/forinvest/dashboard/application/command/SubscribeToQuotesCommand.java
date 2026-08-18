package com.forinvest.dashboard.application.command;

import java.util.List;
import java.util.Objects;

/**
 * A client asking to be sent quotes for a set of symbols until it says otherwise.
 *
 * <p>A command rather than a query: it changes what the application will do on every subsequent
 * tick. Symbols stay raw strings, so an invalid one is reported by the domain instead of being
 * normalised away at the edge.
 *
 * @param subscriberId the connection this subscription belongs to
 * @param tickers the symbols to watch, replacing anything watched before
 */
public record SubscribeToQuotesCommand(String subscriberId, List<String> tickers) {

    public SubscribeToQuotesCommand {
        Objects.requireNonNull(subscriberId, "subscriberId must not be null");
        Objects.requireNonNull(tickers, "tickers must not be null");
        tickers = List.copyOf(tickers);
    }
}
