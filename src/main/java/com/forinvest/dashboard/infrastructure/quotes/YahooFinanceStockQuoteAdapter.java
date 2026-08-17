package com.forinvest.dashboard.infrastructure.quotes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;
import com.forinvest.dashboard.domain.model.StockQuote;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

/**
 * Fetches quotes from Yahoo Finance using the {@code yahoofinance-api} library.
 *
 * <p>The only class that knows Yahoo exists. Everything above it sees the
 * {@link StockQuoteProvider} port and domain types, so replacing the provider means rewriting this
 * file and nothing else.
 *
 * <p><strong>Operational note.</strong> Yahoo's public quote endpoint now rejects unauthenticated
 * callers (HTTP 401/429), so this adapter can legitimately fail in an otherwise healthy system.
 * That is why a provider failure is reported as 502 rather than 500: the request was fine, the
 * dependency was not.
 */
@Component
class YahooFinanceStockQuoteAdapter implements StockQuoteProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YahooFinanceStockQuoteAdapter.class);

    /**
     * The library reads its timeout from this system property into a {@code static final} field
     * when the class initialises, so it has to be set before {@link YahooFinance} is first touched.
     * Doing it in the constructor is safe here because this adapter is the only code that uses the
     * library, and it only references it from methods.
     */
    private static final String TIMEOUT_PROPERTY = "yahoofinance.connection.timeout";

    YahooFinanceStockQuoteAdapter(YahooFinanceProperties properties) {
        System.setProperty(TIMEOUT_PROPERTY, String.valueOf(properties.connectionTimeoutMillis()));
    }

    /**
     * Catching {@code RuntimeException} is deliberate here and suppressed for that reason.
     *
     * <p>This adapter is the anti-corruption layer around a third-party client that throws
     * unchecked exceptions of its own on malformed upstream payloads. Every one of those is an
     * upstream failure, so all of them must become a 502 rather than escaping as a 500. Confining
     * the broad catch to this one boundary method is what keeps the rule useful everywhere else.
     */
    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<StockQuote> findQuotes(List<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return List.of();
        }

        String[] symbols = tickers.stream().map(Ticker::symbol).toArray(String[]::new);

        Map<String, Stock> stocks;
        try {
            // One request for the whole batch — the library joins the symbols into a single query.
            stocks = YahooFinance.get(symbols);
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Yahoo Finance rejected or failed the quote request for {} symbols", symbols.length, failure);
            throw new StockQuoteUnavailableException(
                    "Stock quotes could not be retrieved from the market data provider", failure);
        }

        if (stocks == null) {
            throw new StockQuoteUnavailableException("The market data provider returned no response");
        }

        List<StockQuote> quotes = new ArrayList<>(tickers.size());
        for (Ticker ticker : tickers) {
            toQuote(ticker, stocks.get(ticker.symbol())).ifPresent(quotes::add);
        }
        return List.copyOf(quotes);
    }

    /**
     * Converts one Yahoo result into a domain quote.
     *
     * <p>An unknown symbol comes back as a missing entry, or as a stock with no price. Both mean
     * "no data for this ticker", which the caller reports as unresolved — it is not an error, and
     * it must not be turned into a zero price.
     */
    private static java.util.Optional<StockQuote> toQuote(Ticker ticker, Stock stock) {
        if (stock == null || stock.getQuote() == null) {
            return java.util.Optional.empty();
        }
        BigDecimal price = stock.getQuote().getPrice();
        if (price == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new StockQuote(ticker, price, stock.getQuote().getPreviousClose()));
    }
}
