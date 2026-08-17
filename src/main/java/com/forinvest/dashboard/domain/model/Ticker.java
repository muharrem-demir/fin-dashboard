package com.forinvest.dashboard.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

import com.forinvest.dashboard.domain.exception.InvalidTickerException;

/**
 * A stock ticker symbol, normalised to upper case.
 *
 * <p>Validation lives in the compact constructor, so an invalid ticker cannot be represented
 * anywhere in the system. Normalisation is what makes {@code aapl} and {@code AAPL} the same
 * holding when shares are added.
 */
public record Ticker(String symbol) implements Comparable<Ticker> {

    /** One leading letter, then letters, digits, dot or hyphen; 10 characters at most. */
    private static final Pattern VALID_SYMBOL = Pattern.compile("^[A-Z][A-Z0-9.\\-]{0,9}$");

    public Ticker {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidTickerException("Ticker must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SYMBOL.matcher(symbol).matches()) {
            throw new InvalidTickerException(
                    "Ticker '%s' is not a valid symbol: expected 1-10 characters starting with a letter"
                            .formatted(symbol));
        }
    }

    public static Ticker of(String symbol) {
        return new Ticker(symbol);
    }

    @Override
    public int compareTo(Ticker other) {
        return symbol.compareTo(other.symbol);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
