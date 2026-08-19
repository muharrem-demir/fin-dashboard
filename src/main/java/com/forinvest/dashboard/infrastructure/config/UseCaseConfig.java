package com.forinvest.dashboard.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.application.usecase.AddStockUseCase;
import com.forinvest.dashboard.application.usecase.BroadcastQuoteUpdatesUseCase;
import com.forinvest.dashboard.application.usecase.CreatePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.DeletePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.GetPortfolioUseCase;
import com.forinvest.dashboard.application.usecase.ListPortfoliosUseCase;
import com.forinvest.dashboard.application.usecase.ListStockQuotesUseCase;
import com.forinvest.dashboard.application.usecase.RemoveStockUseCase;
import com.forinvest.dashboard.application.usecase.RenamePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.SubscribeToQuotesUseCase;
import com.forinvest.dashboard.application.usecase.UnsubscribeFromQuotesUseCase;
import com.forinvest.dashboard.domain.model.HistoryWindow;
import com.forinvest.dashboard.domain.port.PortfolioRepository;
import com.forinvest.dashboard.domain.port.QuoteSubscriptionRegistry;
import com.forinvest.dashboard.domain.port.QuoteUpdatePublisher;
import com.forinvest.dashboard.domain.port.StockPriceHistoryProvider;
import com.forinvest.dashboard.domain.port.StockQuoteProvider;
import com.forinvest.dashboard.infrastructure.quotes.QuoteHistoryProperties;

/**
 * Wires the use cases into the Spring context.
 *
 * <p>This class exists so the application layer does not have to. Use cases are plain objects with
 * constructor dependencies; assembling them is a framework concern, and doing it here keeps
 * {@code application} free of {@code @Service} and {@code @Autowired} — which is exactly what
 * {@code CleanArchitectureTest} verifies.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    ListPortfoliosUseCase listPortfoliosUseCase(PortfolioRepository portfolioRepository) {
        return new ListPortfoliosUseCase(portfolioRepository);
    }

    @Bean
    GetPortfolioUseCase getPortfolioUseCase(PortfolioRepository portfolioRepository) {
        return new GetPortfolioUseCase(portfolioRepository);
    }

    @Bean
    CreatePortfolioUseCase createPortfolioUseCase(PortfolioRepository portfolioRepository) {
        return new CreatePortfolioUseCase(portfolioRepository);
    }

    @Bean
    RenamePortfolioUseCase renamePortfolioUseCase(
            PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        return new RenamePortfolioUseCase(portfolioRepository, transactionRunner);
    }

    @Bean
    DeletePortfolioUseCase deletePortfolioUseCase(PortfolioRepository portfolioRepository) {
        return new DeletePortfolioUseCase(portfolioRepository);
    }

    @Bean
    AddStockUseCase addStockUseCase(PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        return new AddStockUseCase(portfolioRepository, transactionRunner);
    }

    @Bean
    RemoveStockUseCase removeStockUseCase(
            PortfolioRepository portfolioRepository, TransactionRunner transactionRunner) {
        return new RemoveStockUseCase(portfolioRepository, transactionRunner);
    }

    /**
     * The configured history window is turned into a domain value here, at startup. A days setting
     * outside what {@link HistoryWindow} allows therefore stops the application from starting,
     * rather than answering the first request that asks for history with a 500.
     */
    @Bean
    ListStockQuotesUseCase listStockQuotesUseCase(
            StockQuoteProvider stockQuoteProvider,
            StockPriceHistoryProvider stockPriceHistoryProvider,
            QuoteHistoryProperties historyProperties) {
        return new ListStockQuotesUseCase(
                stockQuoteProvider, stockPriceHistoryProvider, HistoryWindow.ofDays(historyProperties.days()));
    }

    @Bean
    SubscribeToQuotesUseCase subscribeToQuotesUseCase(QuoteSubscriptionRegistry quoteSubscriptionRegistry) {
        return new SubscribeToQuotesUseCase(quoteSubscriptionRegistry);
    }

    @Bean
    UnsubscribeFromQuotesUseCase unsubscribeFromQuotesUseCase(QuoteSubscriptionRegistry quoteSubscriptionRegistry) {
        return new UnsubscribeFromQuotesUseCase(quoteSubscriptionRegistry);
    }

    @Bean
    BroadcastQuoteUpdatesUseCase broadcastQuoteUpdatesUseCase(
            QuoteSubscriptionRegistry quoteSubscriptionRegistry,
            StockQuoteProvider stockQuoteProvider,
            QuoteUpdatePublisher quoteUpdatePublisher) {
        return new BroadcastQuoteUpdatesUseCase(quoteSubscriptionRegistry, stockQuoteProvider, quoteUpdatePublisher);
    }
}
