package com.forinvest.dashboard.infrastructure.quotes;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches quotes from Yahoo Finance's batch quote endpoint.
 *
 * <p>The only class that knows Yahoo exists. Everything above it sees the
 * {@link StockQuoteProvider} port and domain types, so replacing the provider means rewriting this
 * file and nothing else.
 *
 * <p>This talks to the endpoint directly rather than through the {@code yahoofinance-api} library.
 * The library calls {@code /v7/finance/quote} unauthenticated, which Yahoo now answers with HTTP
 * 401; its crumb support exists only on the historical-download path and cannot be reached from
 * the quote call, so there was no way to configure it into working. See {@link YahooCrumbSession}
 * for the handshake performed instead.
 *
 * <p>Batching is preserved exactly as the port requires: every requested symbol goes up in one
 * request, so the cost of a quote request — and of one tick of the live feed — is set by how often
 * it is asked for, never by how many symbols it covers.
 */
class YahooFinanceStockQuoteAdapter implements StockQuoteProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YahooFinanceStockQuoteAdapter.class);

    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;

    private static final String UNAVAILABLE = "Stock quotes could not be retrieved from the market data provider";

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final YahooCrumbSession session;
    private final YahooFinanceProperties properties;

    YahooFinanceStockQuoteAdapter(HttpClient httpClient, JsonMapper jsonMapper, YahooFinanceProperties properties) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.session = new YahooCrumbSession(httpClient, properties);
    }

    @Override
    public List<StockQuote> findQuotes(List<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return List.of();
        }

        String symbols = tickers.stream().map(Ticker::symbol).collect(Collectors.joining(","));

        HttpResponse<String> response = request(symbols, session.crumb());
        if (isRejected(response.statusCode())) {
            // A crumb expires without warning, and an expired one is indistinguishable from one
            // that was never valid. A single retry behind a fresh handshake turns that into a
            // non-event rather than an outage every caller has to see.
            LOG.debug(
                    "Yahoo Finance rejected the cached crumb (HTTP {}); retrying with a fresh one",
                    response.statusCode());
            session.invalidate();
            response = request(symbols, session.crumb());
        }

        if (response.statusCode() != HTTP_OK) {
            LOG.warn(
                    "Yahoo Finance failed the quote request for {} symbols with HTTP {}",
                    tickers.size(),
                    response.statusCode());
            throw new StockQuoteUnavailableException(UNAVAILABLE);
        }

        return toQuotes(tickers, parse(response.body()));
    }

    private static boolean isRejected(int statusCode) {
        return statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN;
    }

    private HttpResponse<String> request(String symbols, String crumb) {
        URI uri = URI.create("%s?symbols=%s&crumb=%s".formatted(properties.quoteUrl(), encode(symbols), encode(crumb)));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", properties.userAgent())
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(properties.connectionTimeoutMillis()))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            LOG.warn("Yahoo Finance could not be reached for a quote request", failure);
            throw new StockQuoteUnavailableException(UNAVAILABLE, failure);
        } catch (InterruptedException interruption) {
            // Restore the flag so the caller's own cancellation still works; this one request is
            // simply reported as unavailable.
            Thread.currentThread().interrupt();
            throw new StockQuoteUnavailableException(UNAVAILABLE, interruption);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Reads the payload into one node per symbol.
     *
     * <p>Catching {@code RuntimeException} is deliberate here and suppressed for that reason. This
     * adapter is the anti-corruption layer around a third party, and a malformed or truncated
     * payload is an upstream failure however it surfaces — it must become a 502 rather than escape
     * as a 500. Confining the broad catch to this one boundary is what keeps the rule useful
     * everywhere else.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private Map<String, JsonNode> parse(String body) {
        try {
            JsonNode quoteResponse = jsonMapper.readTree(body).path("quoteResponse");
            JsonNode error = quoteResponse.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new StockQuoteUnavailableException("The market data provider reported an error for this request");
            }

            Map<String, JsonNode> bySymbol = new HashMap<>();
            for (JsonNode quote : quoteResponse.path("result")) {
                JsonNode symbol = quote.path("symbol");
                if (symbol.isString()) {
                    bySymbol.put(symbol.stringValue(), quote);
                }
            }
            return bySymbol;
        } catch (StockQuoteUnavailableException alreadyTranslated) {
            throw alreadyTranslated;
        } catch (RuntimeException malformed) {
            LOG.warn("Yahoo Finance returned a payload that could not be read", malformed);
            throw new StockQuoteUnavailableException(UNAVAILABLE, malformed);
        }
    }

    private static List<StockQuote> toQuotes(List<Ticker> tickers, Map<String, JsonNode> bySymbol) {
        List<StockQuote> quotes = new ArrayList<>(tickers.size());
        for (Ticker ticker : tickers) {
            toQuote(ticker, bySymbol.get(ticker.symbol())).ifPresent(quotes::add);
        }
        return List.copyOf(quotes);
    }

    /**
     * Converts one Yahoo result into a domain quote.
     *
     * <p>An unknown symbol is simply absent from the response, and a known one can be quoted
     * without a price. Both mean "no data for this ticker", which the caller reports as unresolved
     * — it is not an error, and it must not be turned into a zero price.
     */
    private static Optional<StockQuote> toQuote(Ticker ticker, JsonNode quote) {
        if (quote == null) {
            return Optional.empty();
        }
        return decimal(quote, "regularMarketPrice")
                .map(price -> new StockQuote(
                        ticker,
                        price,
                        decimal(quote, "regularMarketPreviousClose").orElse(null)));
    }

    /** Absent, null and non-numeric all mean the same thing: the figure is not available. */
    private static Optional<BigDecimal> decimal(JsonNode quote, String field) {
        JsonNode value = quote.path(field);
        return value.isNumber() ? Optional.of(value.decimalValue()) : Optional.empty();
    }
}
