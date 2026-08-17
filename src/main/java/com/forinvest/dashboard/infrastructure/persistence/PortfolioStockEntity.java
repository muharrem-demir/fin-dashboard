package com.forinvest.dashboard.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** JPA mapping of one holding row. A persistence artifact with no rules of its own. */
@Entity
@Table(
        name = "portfolio_stock",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_portfolio_stock_ticker",
                        columnNames = {"portfolio_id", "ticker"}))
class PortfolioStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "portfolio_id",
            nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_portfolio_stock_portfolio"))
    private PortfolioEntity portfolio;

    @Column(name = "ticker", nullable = false, length = 16)
    private String ticker;

    @Column(name = "shares", nullable = false)
    private int shares;

    protected PortfolioStockEntity() {
        // Required by JPA.
    }

    PortfolioStockEntity(String ticker, int shares) {
        this.ticker = ticker;
        this.shares = shares;
    }

    Long getId() {
        return id;
    }

    String getTicker() {
        return ticker;
    }

    int getShares() {
        return shares;
    }

    void setShares(int shares) {
        this.shares = shares;
    }

    void setPortfolio(PortfolioEntity portfolio) {
        this.portfolio = portfolio;
    }
}
