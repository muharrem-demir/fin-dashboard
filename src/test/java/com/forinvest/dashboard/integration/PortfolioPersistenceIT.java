package com.forinvest.dashboard.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/**
 * The storage adapter against a real PostgreSQL.
 *
 * <p>Covers what a mocked repository cannot: that the Flyway schema and the JPA mapping agree, that
 * dropping a holding actually deletes its row rather than orphaning it, and that deleting a
 * portfolio cascades.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PortfolioPersistenceIT {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long stockRowCount(UUID portfolioId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM portfolio_stock WHERE portfolio_id = ?", Long.class, portfolioId);
        return count == null ? 0L : count;
    }

    @Test
    @DisplayName("saves and reloads a portfolio with its holdings intact")
    void roundTripsAggregate() {
        Portfolio saved = portfolioRepository.save(
                Portfolio.create("Growth").addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("MSFT"), 20));

        Portfolio reloaded = portfolioRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("Growth");
        assertThat(reloaded.holdings()).containsExactly(Holding.of("AAPL", 10), Holding.of("MSFT", 20));
    }

    @Test
    @DisplayName("adding to an existing ticker updates the row instead of inserting a second one")
    void updatesExistingHoldingRow() {
        Portfolio saved = portfolioRepository.save(Portfolio.create("Growth").addStock(Ticker.of("AAPL"), 10));

        Portfolio topped = portfolioRepository.save(
                portfolioRepository.findById(saved.id()).orElseThrow().addStock(Ticker.of("aapl"), 5));

        assertThat(topped.holdings()).containsExactly(Holding.of("AAPL", 15));
        assertThat(stockRowCount(saved.id())).isEqualTo(1L);
    }

    @Test
    @DisplayName("removing a holding deletes its row rather than orphaning it")
    void deletesRemovedHoldingRows() {
        Portfolio saved = portfolioRepository.save(
                Portfolio.create("Growth").addStock(Ticker.of("AAPL"), 10).addStock(Ticker.of("MSFT"), 20));

        portfolioRepository.save(
                portfolioRepository.findById(saved.id()).orElseThrow().removeStock(Ticker.of("AAPL")));

        assertThat(stockRowCount(saved.id())).isEqualTo(1L);
        assertThat(portfolioRepository.findById(saved.id()).orElseThrow().holdings())
                .containsExactly(Holding.of("MSFT", 20));
    }

    @Test
    @DisplayName("renaming keeps identity and holdings")
    void persistsRename() {
        Portfolio saved = portfolioRepository.save(Portfolio.create("Growth").addStock(Ticker.of("AAPL"), 10));

        portfolioRepository.save(
                portfolioRepository.findById(saved.id()).orElseThrow().rename("Income"));

        Portfolio reloaded = portfolioRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Income");
        assertThat(reloaded.holdings()).containsExactly(Holding.of("AAPL", 10));
    }

    @Test
    @DisplayName("deleting a portfolio cascades to its holdings")
    void deleteCascades() {
        Portfolio saved = portfolioRepository.save(Portfolio.create("Growth").addStock(Ticker.of("AAPL"), 10));

        assertThat(portfolioRepository.deleteById(saved.id())).isTrue();

        assertThat(portfolioRepository.findById(saved.id())).isEmpty();
        assertThat(stockRowCount(saved.id())).isZero();
    }

    @Test
    @DisplayName("deleting an unknown portfolio reports that nothing was deleted")
    void deleteReportsMissingPortfolio() {
        assertThat(portfolioRepository.deleteById(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("findAll returns the stored portfolios")
    void listsPortfolios() {
        Portfolio saved = portfolioRepository.save(Portfolio.create("Listable " + UUID.randomUUID()));

        List<Portfolio> all = portfolioRepository.findAll();

        assertThat(all).extracting(Portfolio::id).contains(saved.id());
    }

    @Test
    @DisplayName("existsById reflects what is stored")
    void reportsExistence() {
        Portfolio saved = portfolioRepository.save(Portfolio.create("Growth"));

        assertThat(portfolioRepository.existsById(saved.id())).isTrue();
        assertThat(portfolioRepository.existsById(UUID.randomUUID())).isFalse();
    }
}
