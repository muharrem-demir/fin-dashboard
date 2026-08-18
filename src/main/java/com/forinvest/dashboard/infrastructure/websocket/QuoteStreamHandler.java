package com.forinvest.dashboard.infrastructure.websocket;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.forinvest.dashboard.application.command.SubscribeToQuotesCommand;
import com.forinvest.dashboard.application.command.UnsubscribeFromQuotesCommand;
import com.forinvest.dashboard.application.usecase.SubscribeToQuotesUseCase;
import com.forinvest.dashboard.application.usecase.UnsubscribeFromQuotesUseCase;
import com.forinvest.dashboard.domain.exception.DomainException;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.infrastructure.websocket.dto.ConnectedMessage;
import com.forinvest.dashboard.infrastructure.websocket.dto.QuoteStreamCommand;
import com.forinvest.dashboard.infrastructure.websocket.dto.StreamErrorMessage;
import com.forinvest.dashboard.infrastructure.websocket.dto.SubscriptionMessage;

import tools.jackson.core.JacksonException;

/**
 * The client-facing half of the feed: one connection, one subscription, changeable at any time.
 *
 * <p>This is a transport, and it is held to the same rule as a controller — it translates messages
 * into use-case calls and their results back into messages, and decides nothing. The subscriber id
 * is the WebSocket session id, which is what gives every connection its own independent watchlist
 * without a client having to invent an identity or authenticate.
 *
 * <p>Nothing here closes a connection because of a bad message. A mistyped symbol is answered with
 * an error frame and the previous subscription survives, because dropping the socket would cost the
 * client every symbol it had got right.
 */
@Component
class QuoteStreamHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteStreamHandler.class);

    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String USAGE =
            "Expected {\"action\":\"subscribe\",\"tickers\":[\"AAPL\"]} or {\"action\":\"unsubscribe\"}";

    private final SubscribeToQuotesUseCase subscribeToQuotes;
    private final UnsubscribeFromQuotesUseCase unsubscribeFromQuotes;
    private final QuoteStreamSessions sessions;
    private final QuoteStreamProperties properties;

    QuoteStreamHandler(
            SubscribeToQuotesUseCase subscribeToQuotes,
            UnsubscribeFromQuotesUseCase unsubscribeFromQuotes,
            QuoteStreamSessions sessions,
            QuoteStreamProperties properties) {
        this.subscribeToQuotes = subscribeToQuotes;
        this.unsubscribeFromQuotes = unsubscribeFromQuotes;
        this.sessions = sessions;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.open(session);
        LOG.debug("Quote stream opened for session {}", session.getId());
        // A connection on its own subscribes to nothing; the client says what it wants next.
        sessions.send(session.getId(), ConnectedMessage.of(session.getId(), properties.intervalMillis()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();

        QuoteStreamCommand command;
        try {
            command = sessions.read(message.getPayload(), QuoteStreamCommand.class);
        } catch (JacksonException failure) {
            LOG.debug("Unreadable message from session {}", sessionId, failure);
            sessions.send(sessionId, StreamErrorMessage.error(USAGE, Instant.now()));
            return;
        }

        try {
            apply(sessionId, command);
        } catch (DomainException failure) {
            // The client's mistake, not ours and not the provider's: tell it, and leave whatever it
            // was watching before exactly as it was.
            sessions.send(sessionId, StreamErrorMessage.error(failure.getMessage(), Instant.now()));
        }
    }

    private void apply(String sessionId, QuoteStreamCommand command) {
        String action = command.action() == null ? "" : command.action().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case SUBSCRIBE -> {
                QuoteSubscription subscription =
                        subscribeToQuotes.execute(new SubscribeToQuotesCommand(sessionId, command.tickersOrEmpty()));
                sessions.send(sessionId, SubscriptionMessage.subscribed(symbolsOf(subscription)));
            }
            case UNSUBSCRIBE -> {
                unsubscribeFromQuotes.execute(new UnsubscribeFromQuotesCommand(sessionId));
                sessions.send(sessionId, SubscriptionMessage.unsubscribed());
            }
            default ->
                sessions.send(
                        sessionId,
                        StreamErrorMessage.error(
                                "Unknown action '%s'. %s".formatted(command.action(), USAGE), Instant.now()));
        }
    }

    private static List<String> symbolsOf(QuoteSubscription subscription) {
        return subscription.tickers().stream().map(Ticker::symbol).toList();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.close(session.getId());
        // Nothing is left behind for a client that has gone: the next tick fetches only what the
        // clients still connected are watching.
        unsubscribeFromQuotes.execute(new UnsubscribeFromQuotesCommand(session.getId()));
        LOG.debug("Quote stream closed for session {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // Spring closes the session after this, which unsubscribes it through afterConnectionClosed.
        LOG.debug("Transport error on quote stream session {}", session.getId(), exception);
    }
}
