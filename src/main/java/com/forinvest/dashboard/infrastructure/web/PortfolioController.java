package com.forinvest.dashboard.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.forinvest.dashboard.application.command.CreatePortfolioCommand;
import com.forinvest.dashboard.application.command.RenamePortfolioCommand;
import com.forinvest.dashboard.application.usecase.CreatePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.DeletePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.GetPortfolioUseCase;
import com.forinvest.dashboard.application.usecase.ListPortfoliosUseCase;
import com.forinvest.dashboard.application.usecase.RenamePortfolioUseCase;
import com.forinvest.dashboard.domain.model.Portfolio;
import com.forinvest.dashboard.infrastructure.web.dto.CreatePortfolioRequest;
import com.forinvest.dashboard.infrastructure.web.dto.PortfolioResponse;
import com.forinvest.dashboard.infrastructure.web.dto.PortfolioSummaryResponse;
import com.forinvest.dashboard.infrastructure.web.dto.RenamePortfolioRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * HTTP entry point for portfolios themselves.
 *
 * <p>Holdings live under {@code /{portfolioId}/stocks} and are served by
 * {@link PortfolioStockController}, so each controller depends only on the use cases for its own
 * resource.
 *
 * <p>Thin by design: convert the request to a command, call one use case, map the result. There is
 * no error handling here — failures propagate to {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolios", description = "Create, read, rename and delete stock portfolios")
class PortfolioController {

    private final ListPortfoliosUseCase listPortfolios;
    private final GetPortfolioUseCase getPortfolio;
    private final CreatePortfolioUseCase createPortfolio;
    private final RenamePortfolioUseCase renamePortfolio;
    private final DeletePortfolioUseCase deletePortfolio;
    private final PortfolioWebMapper mapper;

    PortfolioController(
            ListPortfoliosUseCase listPortfolios,
            GetPortfolioUseCase getPortfolio,
            CreatePortfolioUseCase createPortfolio,
            RenamePortfolioUseCase renamePortfolio,
            DeletePortfolioUseCase deletePortfolio,
            PortfolioWebMapper mapper) {
        this.listPortfolios = listPortfolios;
        this.getPortfolio = getPortfolio;
        this.createPortfolio = createPortfolio;
        this.renamePortfolio = renamePortfolio;
        this.deletePortfolio = deletePortfolio;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List all portfolios", description = "Returns every portfolio without its holdings.")
    @ApiResponse(responseCode = "200", description = "Portfolios returned")
    ResponseEntity<List<PortfolioSummaryResponse>> listPortfolios() {
        List<PortfolioSummaryResponse> body =
                listPortfolios.execute().stream().map(mapper::toSummaryResponse).toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "Get a portfolio", description = "Returns a single portfolio with all of its holdings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio returned"),
        @ApiResponse(responseCode = "404", description = "No portfolio with this id", content = @Content)
    })
    ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok(mapper.toResponse(getPortfolio.execute(portfolioId)));
    }

    @PostMapping
    @Operation(summary = "Create a portfolio", description = "Creates a new, empty portfolio.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Portfolio created"),
        @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content)
    })
    ResponseEntity<PortfolioResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request, UriComponentsBuilder uriBuilder) {
        Portfolio created = createPortfolio.execute(new CreatePortfolioCommand(request.name()));
        URI location = uriBuilder
                .path("/api/v1/portfolios/{portfolioId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PatchMapping("/{portfolioId}")
    @Operation(summary = "Rename a portfolio", description = "Changes the name, keeping identity and holdings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio renamed"),
        @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content),
        @ApiResponse(responseCode = "404", description = "No portfolio with this id", content = @Content)
    })
    ResponseEntity<PortfolioResponse> renamePortfolio(
            @PathVariable UUID portfolioId, @Valid @RequestBody RenamePortfolioRequest request) {
        Portfolio renamed = renamePortfolio.execute(new RenamePortfolioCommand(portfolioId, request.name()));
        return ResponseEntity.ok(mapper.toResponse(renamed));
    }

    @DeleteMapping("/{portfolioId}")
    @Operation(summary = "Delete a portfolio", description = "Deletes the portfolio together with all its holdings.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Portfolio deleted"),
        @ApiResponse(responseCode = "404", description = "No portfolio with this id", content = @Content)
    })
    ResponseEntity<Void> deletePortfolio(@PathVariable UUID portfolioId) {
        deletePortfolio.execute(portfolioId);
        return ResponseEntity.noContent().build();
    }
}
