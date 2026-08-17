package com.forinvest.dashboard.application;

import java.util.function.Supplier;

import com.forinvest.dashboard.application.port.TransactionRunner;

/**
 * Runs the unit of work immediately, with no transaction.
 *
 * <p>Unit tests care about what a use case does, not that it did so transactionally; the real
 * boundary is exercised by the integration tests. Using this fake instead of a mock keeps the
 * tests readable — no {@code when(...).thenAnswer(...)} boilerplate in every test class.
 */
public final class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
