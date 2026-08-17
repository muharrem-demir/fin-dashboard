package com.forinvest.dashboard.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.forinvest.dashboard.application.port.TransactionRunner;
import com.forinvest.dashboard.application.usecase.AddStockUseCase;
import com.forinvest.dashboard.application.usecase.CreatePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.DeletePortfolioUseCase;
import com.forinvest.dashboard.application.usecase.GetPortfolioUseCase;
import com.forinvest.dashboard.application.usecase.ListPortfoliosUseCase;
import com.forinvest.dashboard.application.usecase.RemoveStockUseCase;
import com.forinvest.dashboard.application.usecase.RenamePortfolioUseCase;
import com.forinvest.dashboard.domain.port.PortfolioRepository;

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
}
