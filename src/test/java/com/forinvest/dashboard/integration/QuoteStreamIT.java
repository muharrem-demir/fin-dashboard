package com.forinvest.dashboard.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The live feed over a real WebSocket connection to a real server.
 *
 * <p>Everything on our side of the quote port is real — the handshake, the handler, the scheduler,
 * the subscription registry, the domain fan-out and the JSON. Only {@link StockQuoteProvider} is
 * replaced, for the same reason the REST integration test replaces it: a build must not fail
 * because a third party rate-limited us, and market data is not deterministic enough to assert on.
 *
 * <p>The tick interval is shortened so the test does not spend seconds waiting for one.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "dashboard.quotes.stream.interval-millis=200")
@Import(TestcontainersConfiguration.class)
class QuoteStreamIT {

    private static final long TIMEOUT_MILLIS = 15_000;

    @LocalServerPort
    private int port;

    @MockitoBean
    private StockQuoteProvider stockQuoteProvider;

    @Autowired
    private QuoteSubscriptionRegistry subscriptionRegistry;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<List<Ticker>> upstreamRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<WebSocketSession> openSessions = new ArrayList<>();
    private final AtomicReference<Function<List<Ticker>, List<StockQuote>>> quoteSource = new AtomicReference<>();

    @BeforeEach
    void stubTheProvider() {
        upstreamRequests.clear();
        quoteSource.set(tickers -> tickers.stream().map(QuoteStreamIT::quote).toList());
        // Stubbed once with an answer the test steers through quoteSource: re-stubbing a mock the
        // scheduler is calling on another thread would be a race.
        when(stockQuoteProvider.findQuotes(anyList())).thenAnswer(invocation -> {
            List<Ticker> requested = invocation.getArgument(0);
            upstreamRequests.add(List.copyOf(requested));
            return quoteSource.get().apply(requested);
        });
    }

    @AfterEach
    void disconnectEveryClient() throws Exception {
        for (WebSocketSession session : openSessions) {
            if (session.isOpen()) {
                session.close();
            }
        }
        openSessions.clear();
        // Waiting for the registry to drain proves the disconnect unsubscribed, and leaves the next
        // test with a feed that is asking the provider for nothing.
        await(
                "every subscription to be dropped",
                () -> subscriptionRegistry.current().isEmpty());
    }

    private static StockQuote quote(Ticker ticker) {
        return new StockQuote(ticker, new BigDecimal("110.00"), new BigDecimal("100.00"));
    }

    private static void await(String what, Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + what);
    }

    private Client connect() throws Exception {
        Client client = new Client(jsonMapper);
        WebSocketSession session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:%d/ws/quotes".formatted(port))
                .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        openSessions.add(session);
        client.session = session;
        return client;
    }

    /** A connected test client that keeps every frame the server sent it. */
    private static final class Client extends TextWebSocketHandler {

        private final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        private final JsonMapper jsonMapper;
        private WebSocketSession session;

        private Client(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(jsonMapper.readTree(message.getPayload()));
        }

        void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
        }

        void subscribe(String... symbols) throws Exception {
            String tickers = java.util.Arrays.stream(symbols)
                    .map("\"%s\""::formatted)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            send("{\"action\":\"subscribe\",\"tickers\":[%s]}".formatted(tickers));
        }

        /** The next frame of this type, skipping anything else that arrives meanwhile. */
        JsonNode next(String type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                JsonNode message = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (message == null) {
                    break;
                }
                if (type.equals(message.get("type").asString())) {
                    return message;
                }
            }
            throw new AssertionError("Timed out waiting for a '" + type + "' frame");
        }

        boolean sawNothing(String type, long millis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < deadline) {
                JsonNode message = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (message != null && type.equals(message.get("type").asString())) {
                    return false;
                }
            }
            return true;
        }

        static List<String> symbolsOf(JsonNode update) {
            return update.get("quotes")
                    .valueStream()
                    .map(quote -> quote.get("ticker").asString())
                    .toList();
        }
    }

    @Test
    @DisplayName("pushes quotes for the subscribed symbols, again on every tick")
    void pushesQuotesPeriodically() throws Exception {
        Client client = connect();

        assertThat(client.next("connected").get("subscriberId").asString()).isNotBlank();

        client.subscribe("AAPL", "MSFT");
        assertThat(client.next("subscribed").get("tickers").valueStream().map(JsonNode::asString))
                .containsExactly("AAPL", "MSFT");

        JsonNode first = client.next("quotes");
        assertThat(Client.symbolsOf(first)).containsExactly("AAPL", "MSFT");
        assertThat(first.get("quotes").get(0).get("percentChange").asDouble()).isEqualTo(10.00);
        assertThat(first.get("timestamp").isNull()).isFalse();
        // The feed carries quotes and nothing else. History is a REST-only concern, fetched one
        // upstream call per ticker: a tick that grew one would stop being a function of the
        // interval alone.
        assertThat(first.has("history")).isFalse();
        assertThat(first.get("quotes").get(0).has("history")).isFalse();

        // A second update with no further request from the client is what makes this a feed.
        assertThat(Client.symbolsOf(client.next("quotes"))).containsExactly("AAPL", "MSFT");
    }

    @Test
    @DisplayName("gives every client its own symbols, and still asks the provider once per tick")
    void servesEveryClientItsOwnSymbols() throws Exception {
        Client one = connect();
        Client two = connect();
        one.subscribe("AAPL", "MSFT");
        two.subscribe("TSLA");
        one.next("subscribed");
        two.next("subscribed");
        one.next("quotes");
        two.next("quotes");

        // Both are subscribed from here on, so every call in this window serves both of them.
        upstreamRequests.clear();
        assertThat(Client.symbolsOf(one.next("quotes"))).containsExactly("AAPL", "MSFT");
        assertThat(Client.symbolsOf(two.next("quotes"))).containsExactly("TSLA");

        List<List<Ticker>> observed = List.copyOf(upstreamRequests);
        assertThat(observed).isNotEmpty();
        assertThat(observed)
                .allSatisfy(request -> assertThat(request)
                        .containsExactlyInAnyOrder(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA")));
    }

    @Test
    @DisplayName("a client may change its symbols at any time, and the next tick honours it")
    void changingSymbolsTakesEffect() throws Exception {
        Client client = connect();
        client.subscribe("AAPL");
        assertThat(Client.symbolsOf(client.next("quotes"))).containsExactly("AAPL");

        client.subscribe("TSLA", "NVDA");
        client.next("subscribed");

        // One tick may already have been in flight with the old symbols when the change arrived.
        List<String> symbols = List.of();
        for (int attempt = 0; attempt < 5 && !symbols.equals(List.of("TSLA", "NVDA")); attempt++) {
            symbols = Client.symbolsOf(client.next("quotes"));
        }
        assertThat(symbols).containsExactly("TSLA", "NVDA");
    }

    @Test
    @DisplayName("unsubscribing stops the updates without closing the connection")
    void unsubscribingStopsTheUpdates() throws Exception {
        Client client = connect();
        client.subscribe("AAPL");
        client.next("quotes");

        client.send("{\"action\":\"unsubscribe\"}");
        client.next("unsubscribed");

        assertThat(client.sawNothing("quotes", 1_000)).isTrue();
        assertThat(client.session.isOpen()).isTrue();
    }

    @Test
    @DisplayName("a provider outage is reported to the client and the feed recovers by itself")
    void reportsAnOutageAndRecovers() throws Exception {
        Client client = connect();
        client.subscribe("AAPL");
        client.next("quotes");

        quoteSource.set(tickers -> {
            throw new StockQuoteUnavailableException("Stock quotes could not be retrieved");
        });
        assertThat(client.next("unavailable").get("message").asString()).contains("could not be retrieved");
        assertThat(client.session.isOpen()).isTrue();

        // The next tick is the retry: nothing has to reconnect or re-subscribe.
        quoteSource.set(tickers -> tickers.stream().map(QuoteStreamIT::quote).toList());
        assertThat(Client.symbolsOf(client.next("quotes"))).containsExactly("AAPL");
    }

    @Test
    @DisplayName("an invalid symbol is an error frame, not a dropped connection")
    void reportsAnInvalidSymbol() throws Exception {
        Client client = connect();

        client.subscribe("1BAD!");

        assertThat(client.next("error").get("message").asString()).contains("1BAD");
        assertThat(client.session.isOpen()).isTrue();

        client.subscribe("AAPL");
        assertThat(Client.symbolsOf(client.next("quotes"))).containsExactly("AAPL");
    }

    @Test
    @DisplayName("a disconnect unsubscribes, so nothing is fetched for a client that has gone")
    void disconnectingUnsubscribes() throws Exception {
        Client client = connect();
        client.subscribe("AAPL");
        client.next("quotes");
        assertThat(subscriptionRegistry.current().isEmpty()).isFalse();

        client.session.close();

        await(
                "the subscription to be dropped",
                () -> subscriptionRegistry.current().isEmpty());
        upstreamRequests.clear();
        Thread.sleep(600);
        assertThat(upstreamRequests).isEmpty();
    }
}
