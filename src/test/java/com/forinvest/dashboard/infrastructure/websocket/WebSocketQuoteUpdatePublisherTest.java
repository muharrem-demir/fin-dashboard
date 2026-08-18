package com.forinvest.dashboard.infrastructure.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.StockQuoteLookup;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.infrastructure.web.StockQuoteWebMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WebSocketQuoteUpdatePublisherTest {

    private static final SubscriberId SUBSCRIBER = SubscriberId.of("session-1");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<WebSocketMessage<?>> sent;

    private QuoteStreamSessions sessions;
    private WebSocketQuoteUpdatePublisher publisher;

    @BeforeEach
    void setUp() {
        lenient().when(session.getId()).thenReturn(SUBSCRIBER.value());
        lenient().when(session.isOpen()).thenReturn(true);

        sessions = new QuoteStreamSessions(jsonMapper, new QuoteStreamProperties(null, null, null, null, null));
        publisher = new WebSocketQuoteUpdatePublisher(sessions, new StockQuoteWebMapper());
    }

    private JsonNode lastMessage() throws Exception {
        verify(session).sendMessage(sent.capture());
        return jsonMapper.readTree(sent.getValue().getPayload().toString());
    }

    @Test
    @DisplayName("pushes the same quote shape the REST endpoint returns, percent change included")
    void pushesQuotesInTheRestShape() throws Exception {
        sessions.open(session);
        StockQuoteLookup lookup = StockQuoteLookup.reconcile(
                List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH")),
                List.of(new StockQuote(Ticker.of("AAPL"), new BigDecimal("110"), new BigDecimal("100"))));

        publisher.publishQuotes(SUBSCRIBER, lookup);

        JsonNode update = lastMessage();
        assertThat(update.get("type").asString()).isEqualTo("quotes");
        assertThat(update.get("quoteCount").asInt()).isEqualTo(1);
        assertThat(update.get("quotes").get(0).get("ticker").asString()).isEqualTo("AAPL");
        assertThat(update.get("quotes").get(0).get("percentChange").asDouble()).isEqualTo(10.00);
        assertThat(update.get("unresolved").get(0).asString()).isEqualTo("NOSUCH");
        assertThat(update.get("timestamp").isNull()).isFalse();
    }

    @Test
    @DisplayName("tells the subscriber the feed is down instead of going quiet")
    void reportsAnOutage() throws Exception {
        sessions.open(session);

        publisher.publishUnavailable(SUBSCRIBER, "upstream refused the request");

        JsonNode message = lastMessage();
        assertThat(message.get("type").asString()).isEqualTo("unavailable");
        assertThat(message.get("message").asString()).isEqualTo("upstream refused the request");
    }

    @Test
    @DisplayName("silently skips a subscriber whose connection has already gone")
    void skipsAClosedConnection() {
        publisher.publishUnavailable(SubscriberId.of("session-gone"), "upstream refused the request");

        verifyNoInteractions(session);
    }
}
