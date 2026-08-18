package com.forinvest.dashboard.infrastructure.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The open connections, and the only place anything is written to one.
 *
 * <p>Two threads write to a session: the container thread answering a client message, and the
 * scheduler thread pushing a tick. A raw {@code WebSocketSession} is not safe for concurrent sends,
 * so every session is wrapped in a {@link ConcurrentWebSocketSessionDecorator}, which serialises
 * writes and closes a client that cannot keep up with the feed instead of letting it block the
 * broadcast for everybody else.
 *
 * <p>A failed send is logged and the session dropped, never rethrown. Delivery to one client is not
 * allowed to cost the other clients their update — which is the promise
 * {@code QuoteUpdatePublisher} makes to the use case.
 */
@Component
class QuoteStreamSessions {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteStreamSessions.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final QuoteStreamProperties properties;

    QuoteStreamSessions(JsonMapper jsonMapper, QuoteStreamProperties properties) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    void open(WebSocketSession session) {
        sessions.put(
                session.getId(),
                new ConcurrentWebSocketSessionDecorator(
                        session, properties.sendTimeLimitMillis(), properties.sendBufferSizeBytes()));
    }

    void close(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Reads a client message. Throws {@link JacksonException} if it is not the shape we expect. */
    <T> T read(String payload, Class<T> type) {
        return jsonMapper.readValue(payload, type);
    }

    /** Serialises a message and sends it, if that session is still there to receive it. */
    void send(String sessionId, Object message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(message)));
        } catch (IOException | JacksonException failure) {
            LOG.debug("Dropping WebSocket session {}: its last message could not be delivered", sessionId, failure);
            sessions.remove(sessionId);
        }
    }

    int size() {
        return sessions.size();
    }
}
