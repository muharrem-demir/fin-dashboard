package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.DirectTransactionRunner;
import com.forinvest.dashboard.application.command.AddStockCommand;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

@ExtendWith(MockitoExtension.class)
class AddStockUseCaseTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PortfolioRepository portfolioRepository;

    @Captor
    private ArgumentCaptor<Portfolio> savedPortfolio;

    private AddStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddStockUseCase(portfolioRepository, new DirectTransactionRunner());
    }

    @Test
    @DisplayName("saves the portfolio with the new holding applied")
    void addsStockToPortfolio() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

        Portfolio result = useCase.execute(new AddStockCommand(PORTFOLIO_ID, "AAPL", 10));

        verify(portfolioRepository).save(savedPortfolio.capture());
        assertThat(savedPortfolio.getValue().holdings()).containsExactly(Holding.of("AAPL", 10));
        assertThat(result.holdings()).containsExactly(Holding.of("AAPL", 10));
    }

    @Test
    @DisplayName("delegates the additive-upsert rule to the domain")
    void sumsSharesForExistingTicker() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10)));
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

        useCase.execute(new AddStockCommand(PORTFOLIO_ID, "aapl", 5));

        verify(portfolioRepository).save(savedPortfolio.capture());
        assertThat(savedPortfolio.getValue().holdings()).containsExactly(Holding.of("AAPL", 15));
    }

    @Test
    @DisplayName("fails when the portfolio does not exist")
    void failsForUnknownPortfolio() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());
        AddStockCommand command = new AddStockCommand(PORTFOLIO_ID, "AAPL", 10);

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(PortfolioNotFoundException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects an invalid ticker before touching storage")
    void rejectsInvalidTickerWithoutLoading() {
        AddStockCommand command = new AddStockCommand(PORTFOLIO_ID, "not a ticker", 10);

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidTickerException.class);

        verify(portfolioRepository, never()).findById(any());
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("normalises the ticker through the domain value object")
    void normalisesTicker() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

        useCase.execute(new AddStockCommand(PORTFOLIO_ID, "  msft ", 3));

        verify(portfolioRepository).save(savedPortfolio.capture());
        assertThat(savedPortfolio.getValue().holdings())
                .extracting(Holding::ticker)
                .containsExactly(Ticker.of("MSFT"));
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
