package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.forinvest.dashboard.domain.exception.InvalidTickerException;

class WatchlistEntryTest {

    @Test
    @DisplayName("upper-cases the symbol, whatever case it was given in")
    void normalisesSymbol() {
        assertThat(WatchlistEntry.watch("aapl").ticker()).isEqualTo(Ticker.of("AAPL"));
        assertThat(WatchlistEntry.watch("  msft  ").ticker().symbol()).isEqualTo("MSFT");
    }

    @Test
    @DisplayName("two entries for the same symbol in different cases carry the same ticker")
    void sameSymbolInAnyCaseIsTheSameTicker() {
        assertThat(WatchlistEntry.watch("aapl").ticker())
                .isEqualTo(WatchlistEntry.watch("AAPL").ticker());
    }

    @Test
    @DisplayName("assigns a fresh identity to every new entry")
    void assignsIdentity() {
        WatchlistEntry first = WatchlistEntry.watch("AAPL");
        WatchlistEntry second = WatchlistEntry.watch("AAPL");

        assertThat(first.id()).isNotNull();
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    @DisplayName("rejects a symbol the domain cannot parse")
    void rejectsInvalidSymbol() {
        assertThatThrownBy(() -> WatchlistEntry.watch("1NOPE!")).isInstanceOf(InvalidTickerException.class);
        assertThatThrownBy(() -> WatchlistEntry.watch("  ")).isInstanceOf(InvalidTickerException.class);
        assertThatThrownBy(() -> WatchlistEntry.watch(null)).isInstanceOf(InvalidTickerException.class);
    }

    @Test
    @DisplayName("cannot exist without an id or a ticker")
    void requiresBothFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new WatchlistEntry(null, Ticker.of("AAPL"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WatchlistEntry(id, null)).isInstanceOf(NullPointerException.class);
    }
}
