package com.forinvest.dashboard.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forinvest.dashboard.application.command.CreatePortfolioCommand;
import com.forinvest.dashboard.domain.exception.InvalidPortfolioNameException;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/** The use cases that are a single repository call: list, get, create and delete. */
@ExtendWith(MockitoExtension.class)
class PortfolioQueryUseCasesTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PortfolioRepository portfolioRepository;

    @Captor
    private ArgumentCaptor<Portfolio> savedPortfolio;

    @Nested
    @DisplayName("ListPortfoliosUseCase")
    class ListPortfolios {

        @Test
        @DisplayName("returns everything the repository holds")
        void returnsAllPortfolios() {
            List<Portfolio> stored = List.of(Portfolio.create("Growth"), Portfolio.create("Income"));
            when(portfolioRepository.findAll()).thenReturn(stored);

            assertThat(new ListPortfoliosUseCase(portfolioRepository).execute()).isEqualTo(stored);
        }

        @Test
        @DisplayName("returns an empty list when there are no portfolios")
        void returnsEmptyList() {
            when(portfolioRepository.findAll()).thenReturn(List.of());

            assertThat(new ListPortfoliosUseCase(portfolioRepository).execute()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GetPortfolioUseCase")
    class GetPortfolio {

        @Test
        @DisplayName("returns the stored portfolio")
        void returnsPortfolio() {
            Portfolio stored = new Portfolio(PORTFOLIO_ID, "Growth", List.of());
            when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stored));

            assertThat(new GetPortfolioUseCase(portfolioRepository).execute(PORTFOLIO_ID))
                    .isEqualTo(stored);
        }

        @Test
        @DisplayName("fails when the portfolio does not exist")
        void failsForUnknownPortfolio() {
            when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());
            GetPortfolioUseCase useCase = new GetPortfolioUseCase(portfolioRepository);

            assertThatThrownBy(() -> useCase.execute(PORTFOLIO_ID))
                    .isInstanceOf(PortfolioNotFoundException.class)
                    .hasMessageContaining(PORTFOLIO_ID.toString());
        }

        @Test
        @DisplayName("rejects a null id")
        void rejectsNullId() {
            GetPortfolioUseCase useCase = new GetPortfolioUseCase(portfolioRepository);

            assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("CreatePortfolioUseCase")
    class CreatePortfolio {

        @Test
        @DisplayName("saves a new empty portfolio with a generated id")
        void createsPortfolio() {
            when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(call -> call.getArgument(0));

            Portfolio result =
                    new CreatePortfolioUseCase(portfolioRepository).execute(new CreatePortfolioCommand("Growth"));

            verify(portfolioRepository).save(savedPortfolio.capture());
            assertThat(savedPortfolio.getValue().name()).isEqualTo("Growth");
            assertThat(savedPortfolio.getValue().holdings()).isEmpty();
            assertThat(result.id()).isNotNull();
        }

        @Test
        @DisplayName("lets the domain reject a blank name")
        void rejectsBlankName() {
            CreatePortfolioUseCase useCase = new CreatePortfolioUseCase(portfolioRepository);
            CreatePortfolioCommand command = new CreatePortfolioCommand("   ");

            assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(InvalidPortfolioNameException.class);
        }

        @Test
        @DisplayName("rejects a null command")
        void rejectsNullCommand() {
            CreatePortfolioUseCase useCase = new CreatePortfolioUseCase(portfolioRepository);

            assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("DeletePortfolioUseCase")
    class DeletePortfolio {

        @Test
        @DisplayName("deletes an existing portfolio")
        void deletesPortfolio() {
            when(portfolioRepository.deleteById(PORTFOLIO_ID)).thenReturn(true);

            new DeletePortfolioUseCase(portfolioRepository).execute(PORTFOLIO_ID);

            verify(portfolioRepository).deleteById(PORTFOLIO_ID);
        }

        @Test
        @DisplayName("fails when the portfolio does not exist")
        void failsForUnknownPortfolio() {
            when(portfolioRepository.deleteById(PORTFOLIO_ID)).thenReturn(false);
            DeletePortfolioUseCase useCase = new DeletePortfolioUseCase(portfolioRepository);

            assertThatThrownBy(() -> useCase.execute(PORTFOLIO_ID)).isInstanceOf(PortfolioNotFoundException.class);
        }

        @Test
        @DisplayName("rejects a null id")
        void rejectsNullId() {
            DeletePortfolioUseCase useCase = new DeletePortfolioUseCase(portfolioRepository);

            assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
