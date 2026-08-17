package com.forinvest.dashboard.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * A market quote for a single ticker: what it trades at now, and what it closed at previously.
 *
 * <p>The percent-change calculation lives here rather than in the Yahoo adapter or a mapper,
 * because it is a business rule and not a detail of any particular quote provider. Swapping the
 * upstream provider must not be able to change what "percent change" means.
 */
public record StockQuote(Ticker ticker, BigDecimal price, BigDecimal previousClose) {

    /** Intermediate scale for the division, so the final 2-decimal figure rounds correctly. */
    private static final int DIVISION_SCALE = 8;

    private static final int PERCENT_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public StockQuote {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(price, "price must not be null");
        // previousClose is intentionally nullable: a newly listed instrument has no prior close,
        // and that is missing data rather than an error.
    }

    /**
     * Percent change against the previous close:
     * {@code (price - previousClose) / previousClose * 100}.
     *
     * <p>Empty when the previous close is unknown or zero — dividing by it would be undefined, and
     * reporting a fabricated 0% would be worse than reporting nothing.
     */
    public Optional<BigDecimal> percentChange() {
        if (previousClose == null || previousClose.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal change = price.subtract(previousClose)
                .divide(previousClose, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return Optional.of(change);
    }
}
