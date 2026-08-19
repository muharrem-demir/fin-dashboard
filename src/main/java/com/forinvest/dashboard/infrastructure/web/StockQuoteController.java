package com.forinvest.dashboard.infrastructure.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forinvest.dashboard.application.query.ListStockQuotesQuery;
import com.forinvest.dashboard.application.usecase.ListStockQuotesUseCase;
import com.forinvest.dashboard.infrastructure.web.dto.StockQuotesResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Market quotes for a batch of tickers.
 *
 * <p>Read-only and unrelated to stored portfolios, which is why it is its own resource rather than
 * an endpoint hanging off {@code /portfolios}.
 */
@RestController
@RequestMapping("/api/v1/stocks")
@Tag(name = "Stock quotes", description = "Live market quotes from the configured quote provider")
class StockQuoteController {

    private final ListStockQuotesUseCase listStockQuotes;
    private final StockQuoteWebMapper mapper;

    StockQuoteController(ListStockQuotesUseCase listStockQuotes, StockQuoteWebMapper mapper) {
        this.listStockQuotes = listStockQuotes;
        this.mapper = mapper;
    }

    /**
     * Quotes several tickers in one call.
     *
     * <p>Spring binds both {@code ?tickers=AAPL,MSFT} and {@code ?tickers=AAPL&tickers=MSFT} to the
     * same list, so either style works.
     */
    @GetMapping("/quotes")
    @Operation(
            summary = "List quotes for multiple tickers",
            description = "Fetches quotes for every requested ticker in a single upstream call and returns the price,"
                    + " the previous close and the percent change against it. Ticker matching is case-insensitive"
                    + " and duplicates are collapsed. Tickers the provider has no data for are listed under"
                    + " `unresolved` rather than being silently dropped. Pass `history=true` to also receive"
                    + " recent daily closes for each ticker; how many trading days that covers is configured"
                    + " per deployment (`dashboard.quotes.history.days`) and is reported on every history"
                    + " entry.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotes returned"),
        @ApiResponse(
                responseCode = "400",
                description = "No tickers given, too many tickers, or an invalid symbol",
                content = @Content),
        @ApiResponse(
                responseCode = "502",
                description = "The market data provider could not be reached or refused the request",
                content = @Content)
    })
    ResponseEntity<StockQuotesResponse> listStockQuotes(
            @RequestParam(name = "tickers") List<String> tickers,
            @Parameter(
                            description = "Also return recent daily closes for each ticker. Off by default:"
                                    + " history costs one upstream call per ticker, so a caller that only wants"
                                    + " prices should not pay for it.")
                    @RequestParam(name = "history", defaultValue = "false")
                    boolean history) {
        return ResponseEntity.ok(
                mapper.toResponse(listStockQuotes.execute(new ListStockQuotesQuery(tickers, history))));
    }
}
