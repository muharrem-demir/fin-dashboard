package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.forinvest.dashboard.domain.exception.InvalidTickerException;

class TickerTest {

    @ParameterizedTest
    @CsvSource({"AAPL, AAPL", "aapl, AAPL", "  msft  , MSFT", "brk.b, BRK.B", "rds-a, RDS-A", "F, F"})
    @DisplayName("normalises to trimmed upper case")
    void normalisesSymbol(String input, String expected) {
        assertThat(Ticker.of(input).symbol()).isEqualTo(expected);
    }

    @Test
    @DisplayName("equality follows the normalised symbol")
    void equalityIsCaseInsensitive() {
        assertThat(Ticker.of("aapl")).isEqualTo(Ticker.of("AAPL")).hasSameHashCodeAs(Ticker.of("AAPL"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "1AAPL", "TOOLONGTICKER", "AA PL", "AA_PL", "AA$", "."})
    @DisplayName("rejects anything that is not a plausible symbol")
    void rejectsInvalidSymbols(String input) {
        assertThatThrownBy(() -> Ticker.of(input)).isInstanceOf(InvalidTickerException.class);
    }

    @Test
    @DisplayName("orders alphabetically by symbol")
    void ordersAlphabetically() {
        assertThat(Ticker.of("AAPL")).isLessThan(Ticker.of("MSFT"));
    }

    @Test
    @DisplayName("renders as the bare symbol")
    void rendersAsSymbol() {
        assertThat(Ticker.of("aapl")).hasToString("AAPL");
    }
}
