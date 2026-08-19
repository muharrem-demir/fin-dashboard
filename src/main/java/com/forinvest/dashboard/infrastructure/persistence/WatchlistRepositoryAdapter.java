package com.forinvest.dashboard.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.forinvest.dashboard.domain.model.Ticker;
import com.forinvest.dashboard.domain.model.WatchlistEntry;
import com.forinvest.dashboard.domain.port.WatchlistRepository;

/**
 * Implements the domain's watchlist port on top of JPA.
 *
 * <p>The only class that knows both models: everything above it speaks {@link WatchlistEntry},
 * everything below it speaks {@link WatchlistEntryEntity}.
 */
@Repository
class WatchlistRepositoryAdapter implements WatchlistRepository {

    private final WatchlistJpaRepository jpaRepository;
    private final WatchlistPersistenceMapper mapper;

    WatchlistRepositoryAdapter(WatchlistJpaRepository jpaRepository, WatchlistPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistEntry> findAll() {
        return jpaRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    /** The symbol is already upper case by the time a {@link Ticker} exists, so this is exact. */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByTicker(Ticker ticker) {
        return jpaRepository.existsByTicker(ticker.symbol());
    }

    @Override
    @Transactional
    public WatchlistEntry save(WatchlistEntry entry) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(entry)));
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
}
