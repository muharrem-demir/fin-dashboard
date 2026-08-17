package com.forinvest.dashboard.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA mapping of the portfolio table.
 *
 * <p>Deliberately anaemic. This is a persistence artifact, not a model: it carries no rules, and
 * nothing outside this package is allowed to see it. All behaviour lives in
 * {@link com.forinvest.dashboard.domain.model.Portfolio}.
 *
 * <p>The id is assigned by the domain rather than generated here, so a portfolio has a stable
 * identity before it ever reaches the database.
 */
@Entity
@Table(name = "portfolio")
class PortfolioEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PortfolioStockEntity> stocks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PortfolioEntity() {
        // Required by JPA.
    }

    PortfolioEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    List<PortfolioStockEntity> getStocks() {
        return stocks;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Keeps both ends of the association consistent, which orphan removal depends on. */
    void addStock(PortfolioStockEntity stock) {
        stocks.add(stock);
        stock.setPortfolio(this);
    }
}
