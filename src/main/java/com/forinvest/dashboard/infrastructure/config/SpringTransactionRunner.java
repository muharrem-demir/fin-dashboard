package com.forinvest.dashboard.infrastructure.config;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.forinvest.dashboard.application.port.TransactionRunner;

/**
 * Supplies the application's {@link TransactionRunner} port using Spring's transaction management.
 *
 * <p>This adapter is the reason use cases can run atomically without importing Spring: the
 * framework dependency stops here, at the infrastructure boundary.
 */
@Component
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
