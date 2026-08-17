package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.forinvest.dashboard.domain.exception.InvalidShareCountException;

class HoldingTest {

    @Test
    @DisplayName("holds a positive position in a ticker")
    void holdsPosition() {
        Holding holding = Holding.of("aapl", 10);

        assertThat(holding.ticker()).isEqualTo(Ticker.of("AAPL"));
        assertThat(holding.shares()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("rejects a non-positive share count")
    void rejectsNonPositiveShares(int shares) {
        assertThatThrownBy(() -> Holding.of("AAPL", shares)).isInstanceOf(InvalidShareCountException.class);
    }

    @Test
    @DisplayName("rejects a null ticker")
    void rejectsNullTicker() {
        assertThatThrownBy(() -> new Holding(null, 10)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("plusShares returns a new holding with the summed position")
    void addsShares() {
        Holding original = Holding.of("AAPL", 10);

        Holding result = original.plusShares(5);

        assertThat(result).isEqualTo(Holding.of("AAPL", 15));
        assertThat(original.shares()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    @DisplayName("plusShares rejects a non-positive addition")
    void rejectsNonPositiveAddition(int additional) {
        Holding holding = Holding.of("AAPL", 10);

        assertThatThrownBy(() -> holding.plusShares(additional)).isInstanceOf(InvalidShareCountException.class);
    }

    @Test
    @DisplayName("plusShares reports overflow rather than wrapping")
    void reportsOverflow() {
        Holding holding = Holding.of("AAPL", Integer.MAX_VALUE);

        assertThatThrownBy(() -> holding.plusShares(1))
                .isInstanceOf(InvalidShareCountException.class)
                .hasMessageContaining("maximum supported share count");
    }
}
