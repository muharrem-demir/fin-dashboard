package com.forinvest.dashboard.infrastructure.websocket;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.port.QuoteUpdatePublisher;
import com.forinvest.dashboard.infrastructure.web.StockQuoteWebMapper;
import com.forinvest.dashboard.infrastructure.websocket.dto.QuoteUpdateMessage;
import com.forinvest.dashboard.infrastructure.websocket.dto.StreamErrorMessage;

/**
 * Delivers an update over the subscriber's own WebSocket connection.
 *
 * <p>The quotes are shaped by the same mapper the REST endpoint uses, so a price looks identical
 * whether it was polled or pushed — and the percent change is still read from the domain, never
 * recomputed at the edge.
 *
 * <p>A subscriber whose connection has since closed is simply skipped by
 * {@link QuoteStreamSessions}, which is why nothing here throws.
 */
@Component
class WebSocketQuoteUpdatePublisher implements QuoteUpdatePublisher {

    private final QuoteStreamSessions sessions;
    private final StockQuoteWebMapper quoteMapper;

    WebSocketQuoteUpdatePublisher(QuoteStreamSessions sessions, StockQuoteWebMapper quoteMapper) {
        this.sessions = sessions;
        this.quoteMapper = quoteMapper;
    }

    @Override
    public void publishQuotes(SubscriberId subscriber, StockQuoteLookup quotes) {
        sessions.send(subscriber.value(), QuoteUpdateMessage.of(quoteMapper.toResponse(quotes), Instant.now()));
    }

    @Override
    public void publishUnavailable(SubscriberId subscriber, String reason) {
        sessions.send(subscriber.value(), StreamErrorMessage.unavailable(reason, Instant.now()));
    }
}
