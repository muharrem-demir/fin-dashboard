package com.forinvest.dashboard.infrastructure.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.Holding;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.model.Ticker;

/**
 * Translates between the JPA entities and the domain aggregate.
 *
 * <p>Written by hand rather than generated: the mapping is small, and the {@code update} direction
 * has to mutate a <em>managed</em> entity graph in place so Hibernate's orphan removal deletes the
 * rows for holdings the domain dropped. A generated mapper would replace the collection instead,
 * which silently breaks that.
 */
@Component
class PortfolioPersistenceMapper {

    Portfolio toDomain(PortfolioEntity entity) {
        List<Holding> holdings = entity.getStocks().stream()
                .map(stock -> new Holding(Ticker.of(stock.getTicker()), stock.getShares()))
                .toList();
        return new Portfolio(entity.getId(), entity.getName(), holdings);
    }

    /** Builds a brand-new entity graph for a portfolio the database has not seen. */
    PortfolioEntity toNewEntity(Portfolio portfolio) {
        PortfolioEntity entity = new PortfolioEntity(portfolio.id(), portfolio.name());
        portfolio
                .holdings()
                .forEach(holding -> entity.addStock(
                        new PortfolioStockEntity(holding.ticker().symbol(), holding.shares())));
        return entity;
    }

    /**
     * Applies the domain state onto a managed entity.
     *
     * <p>Rows are updated in place where the ticker still exists, removed where the domain no longer
     * holds it, and appended where it is new. Mutating the existing collection — rather than
     * assigning a fresh one — is what lets {@code orphanRemoval} issue the DELETEs.
     */
    void updateEntity(PortfolioEntity entity, Portfolio portfolio) {
        entity.setName(portfolio.name());

        Map<String, Integer> desiredShares = new LinkedHashMap<>();
        portfolio
                .holdings()
                .forEach(holding -> desiredShares.put(holding.ticker().symbol(), holding.shares()));

        entity.getStocks().removeIf(stock -> {
            Integer shares = desiredShares.remove(stock.getTicker());
            if (shares == null) {
                return true;
            }
            stock.setShares(shares);
            return false;
        });

        desiredShares.forEach((ticker, shares) -> entity.addStock(new PortfolioStockEntity(ticker, shares)));
    }
}
