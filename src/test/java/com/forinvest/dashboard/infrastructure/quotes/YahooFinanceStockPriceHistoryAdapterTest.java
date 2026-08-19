package com.forinvest.dashboard.infrastructure.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.PricePoint;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Yahoo price-history adapter, driven against a stub of Yahoo running on loopback.
 *
 * <p>Same reasoning as {@link YahooFinanceStockQuoteAdapterTest}: never the real service, and a
 * stub rather than a mocked HTTP client, because what is worth asserting here is what actually goes
 * on the wire — one request per symbol, carrying a period window wide enough to contain the trading
 * days asked for — and what comes back off it.
 *
 * <p>The adapter under test is assembled by {@link QuoteProviderConfig}, so it runs with the
 * production client and its own cookie jar.
 */
class YahooFinanceStockPriceHistoryAdapterTest {

    private static final HistoryWindow FIVE_DAYS = HistoryWindow.ofDays(5);

    /** New York in August: four hours behind UTC. */
    private static final int NEW_YORK_OFFSET_SECONDS = -4 * 3600;

    /** Tokyo: nine hours ahead, so a session opens on a date UTC has not reached yet. */
    private static final int TOKYO_OFFSET_SECONDS = 9 * 3600;

    private HttpServer server;
    private StockPriceHistoryProvider adapter;

    /** How many chart requests each symbol received, so one-call-per-symbol can be asserted. */
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    /** Every raw query string the chart endpoint was called with. */
    private final List<String> queries = new CopyOnWriteArrayList<>();

    private final AtomicReference<Integer> status = new AtomicReference<>(200);

    private final AtomicReference<String> body = new AtomicReference<>("");

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // One context for the whole /chart prefix: the symbol is a path segment, exactly as Yahoo
        // takes it, so a symbol that never reached the URL shows up as a missing counter.
        server.createContext("/chart", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String symbol = path.substring(path.lastIndexOf('/') + 1);
            calls.computeIfAbsent(symbol, key -> new AtomicInteger()).incrementAndGet();
            queries.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, status.get(), body.get());
        });

        server.start();
        adapter = new QuoteProviderConfig()
                .stockPriceHistoryProvider(JsonMapper.builder().build(), yahooProperties(), historyProperties());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static YahooFinanceProperties yahooProperties() {
        return new YahooFinanceProperties(5000, null, null, null, "test-agent");
    }

    private QuoteHistoryProperties historyProperties() {
        return new QuoteHistoryProperties(5, baseUrl() + "/chart");
    }

    private static void respond(HttpExchange exchange, int code, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A chart payload: timestamps and closes as written, plus the exchange's UTC offset. */
    private void respondWithChart(int gmtOffsetSeconds, String timestamps, String closes) {
        status.set(200);
        body.set("""
                {"chart":{"result":[{"meta":{"gmtoffset":%d},"timestamp":[%s],\
                "indicators":{"quote":[{"close":[%s]}]}}],"error":null}}""".formatted(gmtOffsetSeconds, timestamps, closes));
    }

    private static long epochOf(String instant) {
        return Instant.parse(instant).getEpochSecond();
    }

    private int callsTo(String symbol) {
        return calls.getOrDefault(symbol, new AtomicInteger()).get();
    }

    @Test
    @DisplayName("asks for each symbol once and returns its closes oldest first")
    void fetchesOneRequestPerSymbol() {
        respondWithChart(
                NEW_YORK_OFFSET_SECONDS,
                "%d,%d".formatted(epochOf("2026-08-13T13:30:00Z"), epochOf("2026-08-14T13:30:00Z")),
                "148.50,150.25");

        List<PriceHistory> histories = adapter.findHistories(List.of(Ticker.of("AAPL"), Ticker.of("MSFT")), FIVE_DAYS);

        assertThat(callsTo("AAPL")).isEqualTo(1);
        assertThat(callsTo("MSFT")).isEqualTo(1);
        assertThat(histories).hasSize(2);
        assertThat(histories.getFirst().ticker()).isEqualTo(Ticker.of("AAPL"));
        assertThat(histories.getFirst().points())
                .extracting(PricePoint::date)
                .containsExactly(LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-14"));
        assertThat(histories.getFirst().points().getLast().close()).isEqualByComparingTo("150.25");
        assertThat(histories.getFirst().window()).isEqualTo(FIVE_DAYS);
    }

    @Test
    @DisplayName("asks for a calendar span wide enough to contain the trading days wanted")
    void requestsAWideEnoughSpan() {
        respondWithChart(NEW_YORK_OFFSET_SECONDS, String.valueOf(epochOf("2026-08-14T13:30:00Z")), "150.25");

        adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS);

        String query = queries.getFirst();
        assertThat(query).contains("interval=1d");
        long period1 = Long.parseLong(query.replaceAll(".*period1=(\\d+).*", "$1"));
        long period2 = Long.parseLong(query.replaceAll(".*period2=(\\d+).*", "$1"));
        assertThat(period2 - period1)
                .isEqualTo(FIVE_DAYS.calendarSpanDays() * 86_400L)
                .isGreaterThan(5 * 86_400L);
    }

    @Test
    @DisplayName("dates a candle by the exchange's own day, not by UTC")
    void datesCandlesInTheExchangeTimezone() {
        // 23:00 UTC on the 13th is 08:00 on the 14th in Tokyo: the session belongs to the 14th.
        respondWithChart(TOKYO_OFFSET_SECONDS, String.valueOf(epochOf("2026-08-13T23:00:00Z")), "2850.00");

        List<PriceHistory> histories = adapter.findHistories(List.of(Ticker.of("SONY")), FIVE_DAYS);

        assertThat(histories.getFirst().points().getFirst().date()).isEqualTo(LocalDate.parse("2026-08-14"));
    }

    @Test
    @DisplayName("keeps only the window's worth of days when the provider returns more")
    void trimsToTheWindow() {
        respondWithChart(
                NEW_YORK_OFFSET_SECONDS,
                "%d,%d,%d"
                        .formatted(
                                epochOf("2026-08-12T13:30:00Z"),
                                epochOf("2026-08-13T13:30:00Z"),
                                epochOf("2026-08-14T13:30:00Z")),
                "146.00,148.50,150.25");

        List<PriceHistory> histories = adapter.findHistories(List.of(Ticker.of("AAPL")), HistoryWindow.ofDays(2));

        assertThat(histories.getFirst().points())
                .extracting(PricePoint::date)
                .containsExactly(LocalDate.parse("2026-08-13"), LocalDate.parse("2026-08-14"));
    }

    @Test
    @DisplayName("skips a day quoted without a close rather than inventing one")
    void skipsDaysWithoutAClose() {
        respondWithChart(
                NEW_YORK_OFFSET_SECONDS,
                "%d,%d".formatted(epochOf("2026-08-13T13:30:00Z"), epochOf("2026-08-14T13:30:00Z")),
                "null,150.25");

        List<PriceHistory> histories = adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS);

        assertThat(histories.getFirst().points())
                .extracting(PricePoint::date)
                .containsExactly(LocalDate.parse("2026-08-14"));
    }

    @Test
    @DisplayName("omits a symbol the provider has never heard of instead of failing the batch")
    void omitsUnknownSymbols() {
        status.set(404);
        body.set("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}");

        assertThat(adapter.findHistories(List.of(Ticker.of("NOSUCH")), FIVE_DAYS))
                .isEmpty();
    }

    @Test
    @DisplayName("omits a symbol the provider returned no candles for")
    void omitsEmptyCharts() {
        status.set(200);
        body.set("{\"chart\":{\"result\":[],\"error\":null}}");

        assertThat(adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS)).isEmpty();
    }

    @Test
    @DisplayName("does not call the provider at all for an empty ticker list")
    void skipsEmptyRequests() {
        assertThat(adapter.findHistories(List.of(), FIVE_DAYS)).isEmpty();

        assertThat(queries).isEmpty();
    }

    @Test
    @DisplayName("translates a server-side failure into a domain error")
    void translatesServerFailure() {
        status.set(500);
        body.set("upstream exploded");

        assertThatThrownBy(() -> adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS))
                .isInstanceOf(StockQuoteUnavailableException.class)
                .hasMessageContaining("could not be retrieved");
    }

    @Test
    @DisplayName("translates a malformed payload into a domain error")
    void translatesMalformedPayload() {
        status.set(200);
        body.set("{\"chart\": {\"result\": [ truncated");

        assertThatThrownBy(() -> adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("treats an error reported inside a 200 payload as an outage")
    void translatesReportedError() {
        status.set(200);
        body.set("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Internal\"}}}");

        assertThatThrownBy(() -> adapter.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS))
                .isInstanceOf(StockQuoteUnavailableException.class)
                .hasMessageContaining("reported an error");
    }

    @Test
    @DisplayName("fails the whole call when one symbol fails, rather than answering with a hole in it")
    void failsTheBatchWhenOneSymbolFails() {
        status.set(503);
        body.set("");

        assertThatThrownBy(() -> adapter.findHistories(
                        List.of(Ticker.of("AAPL"), Ticker.of("MSFT"), Ticker.of("TSLA")), FIVE_DAYS))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }

    @Test
    @DisplayName("reports an unreachable provider as an outage")
    void translatesConnectionFailure() {
        int deadPort = server.getAddress().getPort();
        server.stop(0);
        StockPriceHistoryProvider unreachable = new QuoteProviderConfig()
                .stockPriceHistoryProvider(
                        JsonMapper.builder().build(),
                        new YahooFinanceProperties(1000, null, null, null, "test-agent"),
                        new QuoteHistoryProperties(5, "http://127.0.0.1:" + deadPort + "/chart"));

        assertThatThrownBy(() -> unreachable.findHistories(List.of(Ticker.of("AAPL")), FIVE_DAYS))
                .isInstanceOf(StockQuoteUnavailableException.class);
    }
}
