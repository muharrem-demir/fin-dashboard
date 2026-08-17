package com.forinvest.dashboard.infrastructure.web;

import org.springframework.stereotype.Component;

import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.infrastructure.web.dto.PortfolioResponse;
import com.forinvest.dashboard.infrastructure.web.dto.PortfolioSummaryResponse;
import com.forinvest.dashboard.infrastructure.web.dto.StockResponse;

/**
 * Turns domain aggregates into the API's response shapes.
 *
 * <p>Keeping this separate from the domain means the wire format can change — adding a summary
 * figure, renaming a field — without touching a single business rule.
 */
@Component
class PortfolioWebMapper {

    PortfolioResponse toResponse(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.id(),
                portfolio.name(),
                portfolio.holdings().stream()
                        .map(holding -> new StockResponse(holding.ticker().symbol(), holding.shares()))
                        .toList(),
                portfolio.holdingCount(),
                portfolio.totalShares());
    }

    PortfolioSummaryResponse toSummaryResponse(Portfolio portfolio) {
        return new PortfolioSummaryResponse(
                portfolio.id(), portfolio.name(), portfolio.holdingCount(), portfolio.totalShares());
    }
}
