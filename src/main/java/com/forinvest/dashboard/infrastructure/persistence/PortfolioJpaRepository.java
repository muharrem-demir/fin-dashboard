package com.forinvest.dashboard.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link PortfolioEntity}.
 *
 * <p>Package-private on purpose: only {@link PortfolioRepositoryAdapter} may use it, so Spring Data
 * types cannot leak into the application or domain layers.
 */
interface PortfolioJpaRepository extends JpaRepository<PortfolioEntity, UUID> {

    List<PortfolioEntity> findAllByOrderByNameAsc();
}
