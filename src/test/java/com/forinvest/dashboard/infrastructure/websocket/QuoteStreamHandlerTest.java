package com.forinvest.dashboard.infrastructure.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.forinvest.dashboard.application.command.SubscribeToQuotesCommand;
import com.forinvest.dashboard.application.command.UnsubscribeFromQuotesCommand;
import com.forinvest.dashboard.application.usecase.SubscribeToQuotesUseCase;
import com.forinvest.dashboard.application.usecase.UnsubscribeFromQuotesUseCase;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.SubscriberId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The transport, with the use cases mocked — the same shape as the {@code @WebMvcTest} slices that
 * cover the controllers. What is asserted here is translation: message in, use case call out, and
 * the JSON that goes back.
 */
@ExtendWith(MockitoExtension.class)
class QuoteStreamHandlerTest {

    private static final String SESSION_ID = "session-1";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private SubscribeToQuotesUseCase subscribeToQuotes;

    @Mock
    private UnsubscribeFromQuotesUseCase unsubscribeFromQuotes;

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<WebSocketMessage<?>> sent;

    private QuoteStreamHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(session.getId()).thenReturn(SESSION_ID);
        lenient().when(session.isOpen()).thenReturn(true);

        QuoteStreamProperties properties = new QuoteStreamProperties(null, null, null, null, null);
        handler = new QuoteStreamHandler(
                subscribeToQuotes, unsubscribeFromQuotes, new QuoteStreamSessions(jsonMapper, properties), properties);
        handler.afterConnectionEstablished(session);
    }

    /** The n-th message the server sent on this connection, parsed. */
    private JsonNode messageAt(int index) throws Exception {
        verify(session, atLeast(index + 1)).sendMessage(sent.capture());
        return jsonMapper.readTree(sent.getAllValues().get(index).getPayload().toString());
    }

    @Test
    @DisplayName("greets a new connection with its subscriber id and how often updates will arrive")
    void greetsANewConnection() throws Exception {
        JsonNode greeting = messageAt(0);

        assertThat(greeting.get("type").asString()).isEqualTo("connected");
        assertThat(greeting.get("subscriberId").asString()).isEqualTo(SESSION_ID);
        assertThat(greeting.get("intervalMillis").asLong()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("subscribes the connection and echoes the symbols back as the domain normalised them")
    void subscribesAndAcknowledges() throws Exception {
        when(subscribeToQuotes.execute(any()))
                .thenReturn(QuoteSubscription.of(SubscriberId.of(SESSION_ID), List.of("AAPL", "MSFT")));

        handler.handleMessage(session, new TextMessage("{\"action\":\"subscribe\",\"tickers\":[\"aapl\",\"MSFT\"]}"));

        ArgumentCaptor<SubscribeToQuotesCommand> command = ArgumentCaptor.captor();
        verify(subscribeToQuotes).execute(command.capture());
        assertThat(command.getValue().subscriberId()).isEqualTo(SESSION_ID);
        assertThat(command.getValue().tickers()).containsExactly("aapl", "MSFT");

        JsonNode ack = messageAt(1);
        assertThat(ack.get("type").asString()).isEqualTo("subscribed");
        assertThat(ack.get("tickers").valueStream().map(JsonNode::asString)).containsExactly("AAPL", "MSFT");
    }

    @Test
    @DisplayName("a later subscribe replaces the symbols, which is how a client changes them")
    void resubscribingChangesTheSymbols() throws Exception {
        when(subscribeToQuotes.execute(any()))
                .thenReturn(QuoteSubscription.of(SubscriberId.of(SESSION_ID), List.of("AAPL")))
                .thenReturn(QuoteSubscription.of(SubscriberId.of(SESSION_ID), List.of("TSLA")));

        handler.handleMessage(session, new TextMessage("{\"action\":\"subscribe\",\"tickers\":[\"AAPL\"]}"));
        handler.handleMessage(session, new TextMessage("{\"action\":\"subscribe\",\"tickers\":[\"TSLA\"]}"));

        assertThat(messageAt(2).get("tickers").valueStream().map(JsonNode::asString))
                .containsExactly("TSLA");
        verify(unsubscribeFromQuotes, never()).execute(any());
    }

    @Test
    @DisplayName("unsubscribes on request and says so")
    void unsubscribesOnRequest() throws Exception {
        handler.handleMessage(session, new TextMessage("{\"action\":\"unsubscribe\"}"));

        ArgumentCaptor<UnsubscribeFromQuotesCommand> command = ArgumentCaptor.captor();
        verify(unsubscribeFromQuotes).execute(command.capture());
        assertThat(command.getValue().subscriberId()).isEqualTo(SESSION_ID);
        assertThat(messageAt(1).get("type").asString()).isEqualTo("unsubscribed");
    }

    @Test
    @DisplayName("reports an invalid symbol as an error and leaves the connection open")
    void reportsAnInvalidSymbol() throws Exception {
        when(subscribeToQuotes.execute(any())).thenThrow(new InvalidTickerException("Ticker 1BAD is not valid"));

        handler.handleMessage(session, new TextMessage("{\"action\":\"subscribe\",\"tickers\":[\"1BAD\"]}"));

        JsonNode error = messageAt(1);
        assertThat(error.get("type").asString()).isEqualTo("error");
        assertThat(error.get("message").asString()).contains("1BAD");
        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("answers an unparseable message with usage rather than dropping the client")
    void reportsAnUnparseableMessage() throws Exception {
        handler.handleMessage(session, new TextMessage("not json at all"));

        assertThat(messageAt(1).get("type").asString()).isEqualTo("error");
        verify(subscribeToQuotes, never()).execute(any());
        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("answers an action nobody understands with usage")
    void reportsAnUnknownAction() throws Exception {
        handler.handleMessage(session, new TextMessage("{\"action\":\"follow\",\"tickers\":[\"AAPL\"]}"));

        assertThat(messageAt(1).get("message").asString()).contains("follow");
        verify(subscribeToQuotes, never()).execute(any());
    }

    @Test
    @DisplayName("unsubscribes a connection that has gone away, so nothing is fetched for it again")
    void unsubscribesOnDisconnect() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        ArgumentCaptor<UnsubscribeFromQuotesCommand> command = ArgumentCaptor.captor();
        verify(unsubscribeFromQuotes).execute(command.capture());
        assertThat(command.getValue().subscriberId()).isEqualTo(SESSION_ID);
    }
}
