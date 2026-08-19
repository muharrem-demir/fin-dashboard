package com.forinvest.dashboard.infrastructure.quotes;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.model.PriceHistory;
import com.forinvest.dashboard.domain.model.PricePoint;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches daily closes from Yahoo Finance's chart endpoint.
 *
 * <p>A second adapter rather than more methods on {@link YahooFinanceStockQuoteAdapter}, because
 * the two talk to different endpoints with different rules. The quote endpoint takes every symbol
 * in one request and demands a crumb; the chart endpoint takes exactly one symbol per request and
 * does not. Keeping them apart is what guarantees the promise made to the streaming path: adding
 * history here cannot change a single byte of the request one tick of the live feed sends.
 *
 * <p>One symbol per request is the endpoint's shape, not a shortcut — there is no batch form of it.
 * The requests are therefore issued concurrently and awaited together, so quoting fifty symbols
 * with history costs roughly one round trip of latency rather than fifty.
 *
 * <p>A window of trading days is asked for as a span of calendar days
 * ({@link HistoryWindow#calendarSpanDays()}) because that is all the endpoint understands; the
 * domain then cuts the answer back to the days actually wanted.
 */
class YahooFinanceStockPriceHistoryAdapter implements StockPriceHistoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YahooFinanceStockPriceHistoryAdapter.class);

    private static final int HTTP_OK = 200;

    /** An unknown symbol is answered with 404 and an error document. That is missing data, not an outage. */
    private static final int HTTP_NOT_FOUND = 404;

    private static final String UNAVAILABLE = "Stock price history could not be retrieved from the provider";

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final YahooFinanceProperties yahooProperties;
    private final QuoteHistoryProperties historyProperties;

    YahooFinanceStockPriceHistoryAdapter(
            HttpClient httpClient,
            JsonMapper jsonMapper,
            YahooFinanceProperties yahooProperties,
            QuoteHistoryProperties historyProperties) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.yahooProperties = yahooProperties;
        this.historyProperties = historyProperties;
    }

    @Override
    public List<PriceHistory> findHistories(List<Ticker> tickers, HistoryWindow window) {
        if (tickers.isEmpty()) {
            return List.of();
        }

        Instant until = Instant.now();
        Instant from = until.minus(window.calendarSpanDays(), ChronoUnit.DAYS);

        List<CompletableFuture<Optional<PriceHistory>>> pending = tickers.stream()
                .map(ticker -> httpClient
                        .sendAsync(request(ticker, from, until), HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> toHistory(ticker, response, window)))
                .toList();

        return collect(pending);
    }

    /**
     * Waits for every request and keeps the histories that came back.
     *
     * <p>One symbol failing fails the whole call. A partial answer silently missing three of the
     * five symbols asked for would be indistinguishable from three symbols the provider has no data
     * for, and a client cannot retry what it was never told had failed.
     */
    private static List<PriceHistory> collect(List<CompletableFuture<Optional<PriceHistory>>> pending) {
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException failure) {
            throw translate(failure);
        }

        List<PriceHistory> histories = new ArrayList<>(pending.size());
        for (CompletableFuture<Optional<PriceHistory>> future : pending) {
            future.join().ifPresent(histories::add);
        }
        return List.copyOf(histories);
    }

    /**
     * Turns whatever went wrong into the one failure the domain knows about.
     *
     * <p>{@code join} wraps everything — a refused connection, a request that timed out, a domain
     * exception thrown while reading a payload — in a {@link CompletionException}. Unwrapping it is
     * what keeps a rejection raised inside a response handler from surfacing as a 500.
     */
    private static StockQuoteUnavailableException translate(CompletionException failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        if (cause instanceof StockQuoteUnavailableException alreadyTranslated) {
            return alreadyTranslated;
        }
        LOG.warn("Yahoo Finance could not be reached for a price-history request", cause);
        return new StockQuoteUnavailableException(UNAVAILABLE, cause);
    }

    private HttpRequest request(Ticker ticker, Instant from, Instant until) {
        URI uri = URI.create("%s/%s?period1=%d&period2=%d&interval=1d"
                .formatted(
                        historyProperties.chartUrl(),
                        URLEncoder.encode(ticker.symbol(), StandardCharsets.UTF_8),
                        from.getEpochSecond(),
                        until.getEpochSecond()));
        return HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", yahooProperties.userAgent())
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(yahooProperties.connectionTimeoutMillis()))
                .build();
    }

    private Optional<PriceHistory> toHistory(Ticker ticker, HttpResponse<String> response, HistoryWindow window) {
        if (response.statusCode() == HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        if (response.statusCode() != HTTP_OK) {
            LOG.warn(
                    "Yahoo Finance failed the price-history request for {} with HTTP {}",
                    ticker.symbol(),
                    response.statusCode());
            throw new StockQuoteUnavailableException(UNAVAILABLE);
        }

        List<PricePoint> points = parse(response.body());
        return points.isEmpty() ? Optional.empty() : Optional.of(PriceHistory.of(ticker, points, window));
    }

    /**
     * Reads the candles out of one chart payload.
     *
     * <p>Catching {@code RuntimeException} is deliberate here and suppressed for that reason, for
     * exactly the reason {@link YahooFinanceStockQuoteAdapter} does it: this adapter is the
     * anti-corruption layer around a third party, and a malformed or truncated payload is an
     * upstream failure however it surfaces — it must become a 502 rather than escape as a 500.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<PricePoint> parse(String body) {
        try {
            JsonNode chart = jsonMapper.readTree(body).path("chart");
            JsonNode error = chart.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new StockQuoteUnavailableException(
                        "The market data provider reported an error for this history request");
            }

            JsonNode result = chart.path("result").path(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").path(0).path("close");

            return toPoints(timestamps, closes, exchangeOffset(result));
        } catch (StockQuoteUnavailableException alreadyTranslated) {
            throw alreadyTranslated;
        } catch (RuntimeException malformed) {
            LOG.warn("Yahoo Finance returned a history payload that could not be read", malformed);
            throw new StockQuoteUnavailableException(UNAVAILABLE, malformed);
        }
    }

    /**
     * Pairs each timestamp with its close.
     *
     * <p>A candle with no close is skipped rather than carried as a gap: Yahoo emits {@code null}
     * closes for days a symbol did not trade, and a history with that day missing is a truer
     * picture than one with a fabricated flat line.
     */
    private static List<PricePoint> toPoints(JsonNode timestamps, JsonNode closes, ZoneOffset offset) {
        List<PricePoint> points = new ArrayList<>(timestamps.size());
        for (int day = 0; day < timestamps.size(); day++) {
            JsonNode timestamp = timestamps.path(day);
            JsonNode close = closes.path(day);
            if (timestamp.isNumber() && close.isNumber()) {
                LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(timestamp.longValue()), offset);
                points.add(new PricePoint(date, close.decimalValue()));
            }
        }
        return points;
    }

    /**
     * The exchange's UTC offset, so a candle lands on the trading day it belongs to.
     *
     * <p>Daily candles are stamped at the exchange's open. Reading them in UTC would move a Tokyo
     * session onto the previous day and label the history with dates no Tokyo trader recognises.
     */
    private static ZoneOffset exchangeOffset(JsonNode result) {
        JsonNode offset = result.path("meta").path("gmtoffset");
        return offset.isNumber() ? ZoneOffset.ofTotalSeconds(offset.intValue()) : ZoneOffset.UTC;
    }
}
