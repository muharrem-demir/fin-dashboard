package com.forinvest.dashboard.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forinvest.dashboard.domain.model.Portfolio;

/**
 * The domain's view of portfolio storage.
 *
 * <p>An outbound port: the domain declares what it needs, and {@code infrastructure.persistence}
 * supplies an adapter. Everything here is expressed in domain types, so no JPA, Spring Data or SQL
 * concept ever reaches the inner layers.
 */
public interface PortfolioRepository {

    List<Portfolio> findAll();

    Optional<Portfolio> findById(UUID id);

    /** Inserts or updates the aggregate, including its holdings, and returns the stored state. */
    Portfolio save(Portfolio portfolio);

    /** Deletes the portfolio and its holdings. Returns {@code false} if no such portfolio existed. */
    boolean deleteById(UUID id);

    boolean existsById(UUID id);
}
