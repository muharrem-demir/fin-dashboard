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
import com.forinvest.dashboard.application.command.RemoveStockCommand;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;
import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

@ExtendWith(MockitoExtension.class)
class RemoveStockUseCaseTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PortfolioRepository portfolioRepository;

    @Captor
    private ArgumentCaptor<Portfolio> savedPortfolio;

    private RemoveStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RemoveStockUseCase(portfolioRepository, new DirectTransactionRunner());
    }

    @Test
    @DisplayName("saves the portfolio without the removed holding")
    void removesStock() {
        Portfolio existing =
                new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10), Holding.of("MSFT", 20)));
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

        useCase.execute(new RemoveStockCommand(PORTFOLIO_ID, "aapl"));

        verify(portfolioRepository).save(savedPortfolio.capture());
        assertThat(savedPortfolio.getValue().holdings()).containsExactly(Holding.of("MSFT", 20));
    }

    @Test
    @DisplayName("fails when the portfolio does not exist")
    void failsForUnknownPortfolio() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());
        RemoveStockCommand command = new RemoveStockCommand(PORTFOLIO_ID, "AAPL");

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(PortfolioNotFoundException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails when the portfolio does not hold the ticker")
    void failsForUnknownTicker() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10)));
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        RemoveStockCommand command = new RemoveStockCommand(PORTFOLIO_ID, "MSFT");

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(StockNotFoundException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
