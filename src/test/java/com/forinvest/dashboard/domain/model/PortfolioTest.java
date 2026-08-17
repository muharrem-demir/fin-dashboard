package com.forinvest.dashboard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.forinvest.dashboard.domain.exception.InvalidPortfolioNameException;
import com.forinvest.dashboard.domain.exception.InvalidShareCountException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;

/**
 * Rules of the portfolio aggregate.
 *
 * <p>No mocks and no Spring context: the domain is pure, so its rules can be stated directly.
 */
class PortfolioTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Portfolio emptyPortfolio() {
        return new Portfolio(PORTFOLIO_ID, "Growth", List.of());
    }

    @Nested
    @DisplayName("addStock")
    class AddStock {

        @Test
        @DisplayName("appends a new holding when the ticker is not yet held")
        void addsNewHolding() {
            Portfolio result = emptyPortfolio().addStock(Ticker.of("AAPL"), 10);

            assertThat(result.holdings()).containsExactly(Holding.of("AAPL", 10));
        }

        @Test
        @DisplayName("sums shares when the ticker is already held")
        void sumsSharesForExistingTicker() {
            Portfolio result = emptyPortfolio().addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("AAPL"), 5);

            assertThat(result.holdings()).containsExactly(Holding.of("AAPL", 15));
        }

        @Test
        @DisplayName("treats a differently-cased ticker as the same holding")
        void mergesCaseInsensitively() {
            Portfolio result = emptyPortfolio().addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("aapl"), 5);

            assertThat(result.holdingCount()).isEqualTo(1);
            assertThat(result.holdings()).containsExactly(Holding.of("AAPL", 15));
        }

        @Test
        @DisplayName("keeps a merged holding in its original position")
        void preservesHoldingOrderOnMerge() {
            Portfolio result = emptyPortfolio()
                    .addStock(Ticker.of("AAPL"), 10)
                    .addStock(Ticker.of("MSFT"), 20)
                    .addStock(Ticker.of("AAPL"), 5);

            assertThat(result.holdings()).containsExactly(Holding.of("AAPL", 15), Holding.of("MSFT", 20));
        }

        @Test
        @DisplayName("leaves the original portfolio untouched")
        void doesNotMutateTheReceiver() {
            Portfolio original = emptyPortfolio();

            original.addStock(Ticker.of("AAPL"), 10);

            assertThat(original.holdings()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("rejects a non-positive share count")
        void rejectsNonPositiveShares(int shares) {
            Portfolio portfolio = emptyPortfolio();
            Ticker aapl = Ticker.of("AAPL");

            assertThatThrownBy(() -> portfolio.addStock(aapl, shares))
                    .isInstanceOf(InvalidShareCountException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("reports overflow instead of wrapping to a negative position")
        void rejectsOverflow() {
            Portfolio portfolio = emptyPortfolio().addStock(Ticker.of("AAPL"), Integer.MAX_VALUE);
            Ticker aapl = Ticker.of("AAPL");

            assertThatThrownBy(() -> portfolio.addStock(aapl, 1))
                    .isInstanceOf(InvalidShareCountException.class)
                    .hasMessageContaining("maximum supported share count");
        }
    }

    @Nested
    @DisplayName("removeStock")
    class RemoveStock {

        @Test
        @DisplayName("drops the whole position for the ticker")
        void removesHolding() {
            Portfolio portfolio =
                    emptyPortfolio().addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("MSFT"), 20);

            Portfolio result = portfolio.removeStock(Ticker.of("AAPL"));

            assertThat(result.holdings()).containsExactly(Holding.of("MSFT", 20));
        }

        @Test
        @DisplayName("matches the ticker regardless of case")
        void removesCaseInsensitively() {
            Portfolio portfolio = emptyPortfolio().addStock(Ticker.of("AAPL"), 10);

            assertThat(portfolio.removeStock(Ticker.of("aapl")).holdings()).isEmpty();
        }

        @Test
        @DisplayName("fails when the portfolio does not hold the ticker")
        void failsForUnknownTicker() {
            Portfolio portfolio = emptyPortfolio().addStock(Ticker.of("AAPL"), 10);
            Ticker msft = Ticker.of("MSFT");

            assertThatThrownBy(() -> portfolio.removeStock(msft))
                    .isInstanceOf(StockNotFoundException.class)
                    .hasMessageContaining("MSFT");
        }
    }

    @Nested
    @DisplayName("rename")
    class Rename {

        @Test
        @DisplayName("changes the name while keeping identity and holdings")
        void renamesInPlace() {
            Portfolio portfolio = emptyPortfolio().addStock(Ticker.of("AAPL"), 10);

            Portfolio result = portfolio.rename("Income");

            assertThat(result.name()).isEqualTo("Income");
            assertThat(result.id()).isEqualTo(portfolio.id());
            assertThat(result.holdings()).isEqualTo(portfolio.holdings());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("rejects a blank name")
        void rejectsBlankName(String name) {
            Portfolio portfolio = emptyPortfolio();

            assertThatThrownBy(() -> portfolio.rename(name)).isInstanceOf(InvalidPortfolioNameException.class);
        }

        @Test
        @DisplayName("rejects a name longer than the supported length")
        void rejectsOverlongName() {
            Portfolio portfolio = emptyPortfolio();
            String tooLong = "x".repeat(Portfolio.MAX_NAME_LENGTH + 1);

            assertThatThrownBy(() -> portfolio.rename(tooLong))
                    .isInstanceOf(InvalidPortfolioNameException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        @DisplayName("trims surrounding whitespace")
        void trimsName() {
            assertThat(emptyPortfolio().rename("  Growth  ").name()).isEqualTo("Growth");
        }
    }

    @Nested
    @DisplayName("construction and queries")
    class ConstructionAndQueries {

        @Test
        @DisplayName("create() generates an identity and starts empty")
        void createStartsEmpty() {
            Portfolio portfolio = Portfolio.create("Growth");

            assertThat(portfolio.id()).isNotNull();
            assertThat(portfolio.name()).isEqualTo("Growth");
            assertThat(portfolio.holdings()).isEmpty();
        }

        @Test
        @DisplayName("holdings are defensively copied from the caller's list")
        void copiesHoldingsDefensively() {
            List<Holding> source = new java.util.ArrayList<>(List.of(Holding.of("AAPL", 10)));

            Portfolio portfolio = new Portfolio(PORTFOLIO_ID, "Growth", source);
            source.clear();

            assertThat(portfolio.holdings()).containsExactly(Holding.of("AAPL", 10));
        }

        @Test
        @DisplayName("rejects duplicate tickers at construction")
        void rejectsDuplicateTickers() {
            List<Holding> duplicates = List.of(Holding.of("AAPL", 10), Holding.of("AAPL", 5));

            assertThatThrownBy(() -> new Portfolio(PORTFOLIO_ID, "Growth", duplicates))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same ticker twice");
        }

        @Test
        @DisplayName("exposes holding lookups and summary figures")
        void exposesQueries() {
            Portfolio portfolio =
                    emptyPortfolio().addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("MSFT"), 20);

            assertThat(portfolio.findHolding(Ticker.of("AAPL"))).contains(Holding.of("AAPL", 10));
            assertThat(portfolio.findHolding(Ticker.of("TSLA"))).isEmpty();
            assertThat(portfolio.holds(Ticker.of("MSFT"))).isTrue();
            assertThat(portfolio.holds(Ticker.of("TSLA"))).isFalse();
            assertThat(portfolio.holdingCount()).isEqualTo(2);
            assertThat(portfolio.totalShares()).isEqualTo(30L);
        }

        @Test
        @DisplayName("the holdings list cannot be modified through its accessor")
        void holdingsAreUnmodifiable() {
            Portfolio portfolio = emptyPortfolio().addStock(Ticker.of("AAPL"), 10);
            List<Holding> holdings = portfolio.holdings();

            assertThatThrownBy(holdings::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
