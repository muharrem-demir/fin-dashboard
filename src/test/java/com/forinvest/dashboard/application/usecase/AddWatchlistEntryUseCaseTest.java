package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.DirectTransactionRunner;
import com.forinvest.dashboard.application.command.AddWatchlistEntryCommand;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.TickerAlreadyWatchedException;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

@ExtendWith(MockitoExtension.class)
class AddWatchlistEntryUseCaseTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Captor
    private ArgumentCaptor<WatchlistEntry> savedEntry;

    private AddWatchlistEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddWatchlistEntryUseCase(watchlistRepository, new DirectTransactionRunner());
    }

    @Test
    @DisplayName("saves the symbol upper-cased, with a generated id")
    void savesNormalisedEntry() {
        when(watchlistRepository.existsByTicker(Ticker.of("AAPL"))).thenReturn(false);
        when(watchlistRepository.save(any(WatchlistEntry.class))).thenAnswer(call -> call.getArgument(0));

        WatchlistEntry result = useCase.execute(new AddWatchlistEntryCommand("aapl"));

        verify(watchlistRepository).save(savedEntry.capture());
        assertThat(savedEntry.getValue().ticker().symbol()).isEqualTo("AAPL");
        assertThat(savedEntry.getValue().id()).isNotNull();
        assertThat(result).isEqualTo(savedEntry.getValue());
    }

    @Test
    @DisplayName("refuses a symbol already watched, whatever case it is asked for in")
    void refusesDuplicate() {
        when(watchlistRepository.existsByTicker(Ticker.of("AAPL"))).thenReturn(true);
        AddWatchlistEntryCommand command = new AddWatchlistEntryCommand("aapl");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TickerAlreadyWatchedException.class)
                .hasMessageContaining("AAPL");

        verify(watchlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("lets the domain reject an unparseable symbol before storage is touched")
    void rejectsInvalidTicker() {
        AddWatchlistEntryCommand command = new AddWatchlistEntryCommand("1NOPE!");

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidTickerException.class);

        verify(watchlistRepository, never()).existsByTicker(any());
        verify(watchlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
