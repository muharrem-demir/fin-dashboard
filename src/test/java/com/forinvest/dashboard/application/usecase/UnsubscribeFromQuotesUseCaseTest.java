package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.command.UnsubscribeFromQuotesCommand;
import com.forinvest.dashboard.domain.model.SubscriberId;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;

@ExtendWith(MockitoExtension.class)
class UnsubscribeFromQuotesUseCaseTest {

    @Mock
    private QuoteSubscriptionRegistry subscriptionRegistry;

    private UnsubscribeFromQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UnsubscribeFromQuotesUseCase(subscriptionRegistry);
    }

    @Test
    @DisplayName("forgets the subscriber")
    void forgetsTheSubscriber() {
        useCase.execute(new UnsubscribeFromQuotesCommand("session-1"));

        verify(subscriptionRegistry).remove(SubscriberId.of("session-1"));
    }

    @Test
    @DisplayName("is idempotent, because a disconnect races with an explicit unsubscribe")
    void isIdempotent() {
        assertThatCode(() -> {
                    useCase.execute(new UnsubscribeFromQuotesCommand("session-1"));
                    useCase.execute(new UnsubscribeFromQuotesCommand("session-1"));
                })
                .doesNotThrowAnyException();

        verify(subscriptionRegistry, times(2)).remove(SubscriberId.of("session-1"));
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
