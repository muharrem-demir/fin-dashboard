package com.forinvest.dashboard.infrastructure.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forinvest.dashboard.application.command.AddStockCommand;
import com.forinvest.dashboard.application.command.RemoveStockCommand;
import com.forinvest.dashboard.application.usecase.AddStockUseCase;
import com.forinvest.dashboard.application.usecase.RemoveStockUseCase;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.infrastructure.web.dto.AddStockRequest;
import com.forinvest.dashboard.infrastructure.web.dto.PortfolioResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * HTTP entry point for the holdings inside a portfolio.
 *
 * <p>Split out from {@link PortfolioController} so each controller depends only on the use cases
 * for the resource it serves.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/stocks")
@Tag(name = "Portfolio stocks", description = "Add and remove the stock holdings inside a portfolio")
class PortfolioStockController {

    private final AddStockUseCase addStock;
    private final RemoveStockUseCase removeStock;
    private final PortfolioWebMapper mapper;

    PortfolioStockController(AddStockUseCase addStock, RemoveStockUseCase removeStock, PortfolioWebMapper mapper) {
        this.addStock = addStock;
        this.removeStock = removeStock;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(
            summary = "Add shares to a portfolio",
            description = "Adds shares of a ticker. If the portfolio already holds that ticker, the new shares are"
                    + " added to the existing position rather than replacing it; otherwise a new holding is"
                    + " created. Ticker matching is case-insensitive.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shares added; the updated portfolio is returned"),
        @ApiResponse(responseCode = "400", description = "Invalid ticker or share count", content = @Content),
        @ApiResponse(responseCode = "404", description = "No portfolio with this id", content = @Content)
    })
    ResponseEntity<PortfolioResponse> addStock(
            @PathVariable UUID portfolioId, @Valid @RequestBody AddStockRequest request) {
        Portfolio updated = addStock.execute(new AddStockCommand(portfolioId, request.ticker(), request.shares()));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{ticker}")
    @Operation(
            summary = "Remove a stock from a portfolio",
            description = "Removes the whole position in a ticker. Ticker matching is case-insensitive.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Holding removed"),
        @ApiResponse(responseCode = "400", description = "Invalid ticker", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "No such portfolio, or the portfolio does not hold the ticker",
                content = @Content)
    })
    ResponseEntity<Void> removeStock(@PathVariable UUID portfolioId, @PathVariable String ticker) {
        removeStock.execute(new RemoveStockCommand(portfolioId, ticker));
        return ResponseEntity.noContent().build();
    }
}
