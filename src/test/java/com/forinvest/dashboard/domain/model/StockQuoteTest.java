package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The percent-change rule: {@code (price - previousClose) / previousClose * 100}. */
class StockQuoteTest {

    private static StockQuote quote(String price, String previousClose) {
        return new StockQuote(
                Ticker.of("AAPL"), new BigDecimal(price), previousClose == null ? null : new BigDecimal(previousClose));
    }

    @ParameterizedTest
    @CsvSource({
        // price, previousClose, expected percent change
        "110.00,  100.00,  10.00",
        "90.00,   100.00, -10.00",
        "100.00,  100.00,   0.00",
        "150.25,  148.50,   1.18",
        "0.50,      0.40,  25.00",
        "1.00,   1000.00, -99.90"
    })
    @DisplayName("computes percent change against the previous close")
    void computesPercentChange(String price, String previousClose, String expected) {
        assertThat(quote(price, previousClose).percentChange()).contains(new BigDecimal(expected));
    }

    @Test
    @DisplayName("rounds to two decimal places, half up")
    void roundsToTwoDecimals() {
        // 3.005% before rounding
        assertThat(quote("103.005", "100").percentChange()).contains(new BigDecimal("3.01"));
    }

    @Test
    @DisplayName("is undefined when the previous close is zero rather than reporting a fake zero")
    void undefinedForZeroPreviousClose() {
        assertThat(quote("10.00", "0.00").percentChange()).isEmpty();
    }

    @Test
    @DisplayName("is undefined when the previous close is unknown")
    void undefinedForMissingPreviousClose() {
        assertThat(quote("10.00", null).percentChange()).isEmpty();
    }

    @Test
    @DisplayName("handles a negative previous close without inverting the sign of the result")
    void handlesNegativePreviousClose() {
        // Not a realistic equity price, but the arithmetic must stay faithful to the formula.
        assertThat(quote("-5", "-10").percentChange()).contains(new BigDecimal("-50.00"));
    }

    @Test
    @DisplayName("requires a ticker and a price")
    void requiresTickerAndPrice() {
        assertThatThrownBy(() -> new StockQuote(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockQuote(Ticker.of("AAPL"), null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
    }
}
