package com.forinvest.dashboard.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

/**
 * Implements the domain's storage port on top of JPA.
 *
 * <p>The only class that knows both the domain model and the persistence model. Everything above it
 * speaks {@link Portfolio}; everything below it speaks {@link PortfolioEntity}.
 */
@Repository
class PortfolioRepositoryAdapter implements PortfolioRepository {

    private final PortfolioJpaRepository jpaRepository;
    private final PortfolioPersistenceMapper mapper;

    PortfolioRepositoryAdapter(PortfolioJpaRepository jpaRepository, PortfolioPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Portfolio> findAll() {
        return jpaRepository.findAllByOrderByNameAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Portfolio> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    /**
     * Inserts a new aggregate, or applies the domain state onto the managed one.
     *
     * <p>Loading first is what makes updates correct: the mapper mutates the managed collection so
     * removed holdings are deleted rather than orphaned. Blindly merging a detached graph would
     * leave stale rows behind.
     */
    @Override
    @Transactional
    public Portfolio save(Portfolio portfolio) {
        PortfolioEntity entity = jpaRepository
                .findById(portfolio.id())
                .map(managed -> {
                    mapper.updateEntity(managed, portfolio);
                    return managed;
                })
                .orElseGet(() -> mapper.toNewEntity(portfolio));
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return false;
        }
        jpaRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
}
