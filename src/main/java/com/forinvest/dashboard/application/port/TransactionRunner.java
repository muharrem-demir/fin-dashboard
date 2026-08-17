package com.forinvest.dashboard.application.port;

import java.util.function.Supplier;

/**
 * Runs a unit of work atomically.
 *
 * <p>Read-modify-write use cases load an aggregate, apply a domain rule and save the result; those
 * three steps must not interleave with another request. This port expresses that requirement
 * without dragging {@code @Transactional} — and therefore Spring — into the application layer.
 * {@code infrastructure.config} supplies the real implementation.
 */
@FunctionalInterface
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
