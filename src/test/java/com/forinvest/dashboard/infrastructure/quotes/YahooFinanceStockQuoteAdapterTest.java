package com.forinvest.dashboard.infrastructure.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Yahoo adapter, driven against a stub of Yahoo running on loopback.
 *
 * <p>Deliberately never touches the real service: Yahoo's availability must not decide whether this
 * build passes. A stub rather than a mocked HTTP client, because the thing most likely to break
 * here is the cookie/crumb handshake itself — and a mock that returns canned responses would assert
 * that we wrote the code we wrote, not that the exchange works.
 *
 * <p>The adapter under test is assembled by {@link QuoteProviderConfig}, so the cookie jar and its
 * policy are the production ones.
 */
class YahooFinanceStockQuoteAdapterTest {

    private static final String CRUMB = "HxSGRJRAGBp";
    private static final String COOKIE = "A3=d=AQABBb; Path=/; Domain=localhost";

    private HttpServer server;
    private StockQuoteProvider adapter;

    /** How many times each stub endpoint was called, so batching and caching can be asserted. */
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    /** The raw query string the last quote request arrived with. */
    private final AtomicReference<String> lastQuoteQuery = new AtomicReference<>();

    /** Status the quote endpoint answers with; the tests steer rejection through this. */
    private final AtomicReference<Integer> quoteStatus = new AtomicReference<>(200);

    /** Body the quote endpoint answers with. */
    private final AtomicReference<String> quoteBody = new AtomicReference<>("""
            {"quoteResponse":{"result":[],"error":null}}""");

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/cookie", exchange -> {
            count("cookie");
            // Yahoo's cookie host answers 404; the Set-Cookie header on it is the point.
            exchange.getResponseHeaders().add("Set-Cookie", COOKIE);
            respond(exchange, 404, "");
        });

        server.createContext("/getcrumb", exchange -> {
            count("getcrumb");
            boolean hasCookie = exchange.getRequestHeaders().containsKey("Cookie");
            // Mirrors the real behaviour: no cookie, no crumb.
            respond(exchange, hasCookie ? 200 : 401, hasCookie ? CRUMB : "Invalid Cookie");
        });

        server.createContext("/quote", exchange -> {
            count("quote");
            // Raw, not getQuery(): the point is what actually went on the wire, escapes and all.
            lastQuoteQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, quoteStatus.get(), quoteBody.get());
        });

        server.start();
        adapter = new QuoteProviderConfig()
                .stockQuoteProvider(JsonMapper.builder().build(), properties());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private YahooFinanceProperties properties() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new YahooFinanceProperties(5000, base + "/quote", base + "/getcrumb", base + "/cookie", "test-agent");
    }

    private void count(String endpoint) {
        calls.computeIfAbsent(endpoint, key -> new AtomicInteger()).incrementAndGet();
    }

    private int callsTo(String endpoint) {
        return calls.getOrDefault(endpoint, new AtomicInteger()).get();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void respondWithQuotes(String resultJson) {
        quoteStatus.set(200);
        quoteBody.set("{\"quoteResponse\":{\"result\":[%s],\"error\":null}}".formatted(resultJson));
    }

    private static String quoteJson(String symbol, String price, String previousClose) {
        StringBuilder json = new StringBuilder("{\"symbol\":\"").append(symbol).append('"');
        if (price != null) {
            json.append(",\"regularMarketPrice\":").append(price);
        }
        if (previousClose != null) {
            json.append(",\"regularMarketPreviousClose\":").append(previousClose);
        }
        return json.append('}').toString();
    }

    @Test
    @DisplayName("fetches every ticker in one upstream call")
    void batchesIntoASingleCall() {
        respondWithQuotes(quoteJson("AAPL", "150.25", "148.50") + "," + quoteJson("MSFT", "198.00", "200.00"));

        List<StockQuote> quotes = adapter.findQuotes(List.of(Ticker.of("AAPL"), Ticker.of("MSFT")));

        assertThat(callsTo("quote")).isEqualTo(1);
        assertThat(lastQuoteQuery.get()).contains("symbols=AAPL%2CMSFT");
        assertThat(quotes).hasSize(2);
        assertThat(quotes.getFirst().ticker()).isEqualTo(Ticker.of("AAPL"));
        assertThat(quotes.getFirst().price()).isEqualByComparingTo("150.25");
        assertThat(quotes.getFirst().percentChange()).contains(new BigDecimal("1.18"));
        assertThat(quotes.getLast().percentChange()).contains(new BigDecimal("-1.00"));
    }

    @Test
    @DisplayName("performs the cookie handshake and sends the crumb it was issued")
    void authenticatesBeforeQuoting() {
        respondWithQuotes(quoteJson("AAPL", "150.00", "150.00"));

        adapter.findQuotes(List.of(Ticker.of("AAPL")));

        assertThat(callsTo("cookie")).isEqualTo(1);
        assertThat(callsTo("getcrumb")).isEqualTo(1);
        assertThat(lastQuoteQuery.get()).contains("crumb=" + CRUMB);
    }

    @Test
    @DisplayName("reuses the crumb across requests rather than re-handshaking each time")
    void cachesTheCrumb() {
        respondWithQuotes(quoteJson("AAPL", "150.00", "150.00"));

        adapter.findQuotes(List.of(Ticker.of("AAPL")));
        adapter.findQuotes(List.of(Ticker.of("AAPL")));
        adapter.findQuotes(List.of(Ticker.of("AAPL")));

        assertThat(callsTo("quote")).isEqualTo(3);
        assertThat(callsTo("getcrumb")).isEqualTo(1);
    }

    @Test
    @DisplayName("re-handshakes and retries once when the cached crumb is rejected")
    void refreshesARejectedCrumb() {
        quoteStatus.set(401);
        quoteBody.set("{\"finance\":{\"error\":{\"code\":\"Unauthorized\"}}}");

        // First call primes the cache and fails; the crumb it cached is now known-stale.
        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class);
        int handshakesAfterFailure = callsTo("getcrumb");

        respondWithQuotes(quoteJson("AAPL", "150.00", "148.50"));
        List<StockQuote> quotes = adapter.findQuotes(List.of(Ticker.of("AAPL")));

        assertThat(quotes).hasSize(1);
        // Two handshakes for the failed call: the initial one, then the refresh after the 401.
        assertThat(handshakesAfterFailure).isEqualTo(2);
    }

    @Test
    @DisplayName("gives up as a domain error when the crumb is rejected even after a refresh")
    void translatesPersistentRejection() {
        quoteStatus.set(401);
        quoteBody.set("{\"finance\":{\"error\":{\"code\":\"Unauthorized\"}}}");

        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class)
                .hasMessageContaining("could not be retrieved");

        assertThat(callsTo("quote")).isEqualTo(2);
    }

    @Test
    @DisplayName("omits a ticker the provider did not return")
    void omitsMissingSymbols() {
        respondWithQuotes(quoteJson("AAPL", "150.00", "150.00"));

        List<StockQuote> quotes = adapter.findQuotes(List.of(Ticker.of("AAPL"), Ticker.of("NOSUCH")));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().ticker()).isEqualTo(Ticker.of("AAPL"));
    }

    @Test
    @DisplayName("omits a ticker quoted without a price rather than inventing zero")
    void omitsPricelessQuotes() {
        respondWithQuotes(quoteJson("AAPL", null, "150.00"));

        assertThat(adapter.findQuotes(List.of(Ticker.of("AAPL")))).isEmpty();
    }

    @Test
    @DisplayName("keeps a quote whose previous close is unknown, leaving percent change undefined")
    void keepsQuotesWithoutPreviousClose() {
        respondWithQuotes(quoteJson("NEWCO", "12.00", null));

        List<StockQuote> quotes = adapter.findQuotes(List.of(Ticker.of("NEWCO")));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().percentChange()).isEmpty();
    }

    @Test
    @DisplayName("translates a server-side failure into a domain error")
    void translatesServerFailure() {
        quoteStatus.set(500);
        quoteBody.set("upstream exploded");

        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class)
                .hasMessageContaining("could not be retrieved");
    }

    @Test
    @DisplayName("translates a malformed payload into a domain error")
    void translatesMalformedPayload() {
        quoteStatus.set(200);
        quoteBody.set("{\"quoteResponse\": {\"result\": [ truncated");

        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("treats an error reported inside a 200 payload as an outage")
    void translatesReportedError() {
        quoteStatus.set(200);
        quoteBody.set("{\"quoteResponse\":{\"result\":null,\"error\":\"Invalid Crumb\"}}");

        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("reports a refusal to issue a crumb as an outage")
    void translatesCrumbRefusal() throws IOException {
        // Replace the cookie endpoint with one that hands out nothing, so the crumb call is
        // unauthenticated exactly as an anonymous caller's would be.
        server.removeContext("/cookie");
        server.createContext("/cookie", exchange -> respond(exchange, 404, ""));

        assertThatThrownBy(() -> adapter.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class)
                .hasMessageContaining("authorise");

        assertThat(callsTo("quote")).isZero();
    }

    @Test
    @DisplayName("does not call the provider at all for an empty ticker list")
    void skipsEmptyRequests() {
        assertThat(adapter.findQuotes(List.of())).isEmpty();

        assertThat(callsTo("cookie")).isZero();
        assertThat(callsTo("getcrumb")).isZero();
        assertThat(callsTo("quote")).isZero();
    }

    @Test
    @DisplayName("reports an unreachable provider as an outage")
    void translatesConnectionFailure() {
        // A port nothing is listening on: the connection is refused rather than answered.
        int deadPort = server.getAddress().getPort();
        server.stop(0);
        StockQuoteProvider unreachable = new QuoteProviderConfig()
                .stockQuoteProvider(
                        JsonMapper.builder().build(),
                        new YahooFinanceProperties(
                                1000,
                                URI.create("http://127.0.0.1:" + deadPort + "/quote")
                                        .toString(),
                                "http://127.0.0.1:" + deadPort + "/getcrumb",
                                "http://127.0.0.1:" + deadPort + "/cookie",
                                "test-agent"));

        assertThatThrownBy(() -> unreachable.findQuotes(List.of(Ticker.of("AAPL"))))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }
}
