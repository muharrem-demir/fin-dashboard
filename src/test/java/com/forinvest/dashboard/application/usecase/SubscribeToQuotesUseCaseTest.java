package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.command.SubscribeToQuotesCommand;
import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.model.QuoteSubscription;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;

@ExtendWith(MockitoExtension.class)
class SubscribeToQuotesUseCaseTest {

    @Mock
    private QuoteSubscriptionRegistry subscriptionRegistry;

    @Captor
    private ArgumentCaptor<QuoteSubscription> saved;

    private SubscribeToQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SubscribeToQuotesUseCase(subscriptionRegistry);
    }

    @Test
    @DisplayName("registers the subscriber's normalised, de-duplicated symbols")
    void registersNormalisedSymbols() {
        QuoteSubscription subscription =
                useCase.execute(new SubscribeToQuotesCommand("session-1", List.of("aapl", "MSFT", "AAPL")));

        verify(subscriptionRegistry).save(saved.capture());
        assertThat(saved.getValue().subscriber()).isEqualTo(SubscriberId.of("session-1"));
        assertThat(saved.getValue().tickers()).containsExactly(Ticker.of("AAPL"), Ticker.of("MSFT"));
        assertThat(subscription).isEqualTo(saved.getValue());
    }

    @Test
    @DisplayName("changing symbols is the same call again: the newest set replaces the old one")
    void changingSymbolsReplacesTheSubscription() {
        useCase.execute(new SubscribeToQuotesCommand("session-1", List.of("AAPL")));
        useCase.execute(new SubscribeToQuotesCommand("session-1", List.of("TSLA", "NVDA")));

        verify(subscriptionRegistry, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().tickers()).containsExactly(Ticker.of("TSLA"), Ticker.of("NVDA"));
    }

    @Test
    @DisplayName("rejects an invalid symbol without touching the existing subscription")
    void rejectsInvalidSymbol() {
        SubscribeToQuotesCommand command = new SubscribeToQuotesCommand("session-1", List.of("AAPL", "1BAD!"));

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidTickerException.class);

        verify(subscriptionRegistry, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("rejects a subscription to no symbols at all")
    void rejectsEmptySubscription() {
        SubscribeToQuotesCommand command = new SubscribeToQuotesCommand("session-1", List.of());

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidQuoteRequestException.class);

        verify(subscriptionRegistry, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
