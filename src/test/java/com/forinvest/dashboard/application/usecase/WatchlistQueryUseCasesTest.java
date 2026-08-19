package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.domain.exception.WatchlistEntryNotFoundException;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

/** The two watchlist use cases that only pass a call through to the port. */
@ExtendWith(MockitoExtension.class)
class WatchlistQueryUseCasesTest {

    private static final UUID ENTRY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private WatchlistRepository watchlistRepository;

    @Test
    @DisplayName("listing returns what the repository holds, in its order")
    void listsWatchlist() {
        List<WatchlistEntry> stored =
                List.of(new WatchlistEntry(ENTRY_ID, Ticker.of("AAPL")), WatchlistEntry.watch("MSFT"));
        when(watchlistRepository.findAll()).thenReturn(stored);

        assertThat(new ListWatchlistUseCase(watchlistRepository).execute()).isEqualTo(stored);
    }

    @Test
    @DisplayName("removing deletes the entry by id")
    void removesEntry() {
        when(watchlistRepository.deleteById(ENTRY_ID)).thenReturn(true);

        new RemoveWatchlistEntryUseCase(watchlistRepository).execute(ENTRY_ID);

        verify(watchlistRepository).deleteById(ENTRY_ID);
    }

    @Test
    @DisplayName("removing an unknown entry is reported, not silently accepted")
    void reportsUnknownEntry() {
        when(watchlistRepository.deleteById(ENTRY_ID)).thenReturn(false);
        RemoveWatchlistEntryUseCase useCase = new RemoveWatchlistEntryUseCase(watchlistRepository);

        assertThatThrownBy(() -> useCase.execute(ENTRY_ID))
                .isInstanceOf(WatchlistEntryNotFoundException.class)
                .hasMessageContaining(ENTRY_ID.toString());
    }

    @Test
    @DisplayName("rejects a null id")
    void rejectsNullId() {
        RemoveWatchlistEntryUseCase useCase = new RemoveWatchlistEntryUseCase(watchlistRepository);

        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
