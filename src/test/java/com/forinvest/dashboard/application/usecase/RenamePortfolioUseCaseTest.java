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
import com.forinvest.dashboard.application.command.RenamePortfolioCommand;
import com.forinvest.dashboard.domain.exception.InvalidPortfolioNameException;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

@ExtendWith(MockitoExtension.class)
class RenamePortfolioUseCaseTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PortfolioRepository portfolioRepository;

    @Captor
    private ArgumentCaptor<Portfolio> savedPortfolio;

    private RenamePortfolioUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenamePortfolioUseCase(portfolioRepository, new DirectTransactionRunner());
    }

    @Test
    @DisplayName("saves the portfolio under the new name, keeping holdings")
    void renamesPortfolio() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of(Holding.of("AAPL", 10)));
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

        useCase.execute(new RenamePortfolioCommand(PORTFOLIO_ID, "Income"));

        verify(portfolioRepository).save(savedPortfolio.capture());
        assertThat(savedPortfolio.getValue().name()).isEqualTo("Income");
        assertThat(savedPortfolio.getValue().id()).isEqualTo(PORTFOLIO_ID);
        assertThat(savedPortfolio.getValue().holdings()).containsExactly(Holding.of("AAPL", 10));
    }

    @Test
    @DisplayName("fails when the portfolio does not exist")
    void failsForUnknownPortfolio() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());
        RenamePortfolioCommand command = new RenamePortfolioCommand(PORTFOLIO_ID, "Income");

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(PortfolioNotFoundException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("lets the domain reject a blank name")
    void rejectsBlankName() {
        Portfolio existing = new Portfolio(PORTFOLIO_ID, "Growth", List.of());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(existing));
        RenamePortfolioCommand command = new RenamePortfolioCommand(PORTFOLIO_ID, "  ");

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidPortfolioNameException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
