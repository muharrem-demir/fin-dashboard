package com.forinvest.dashboard.domain.exception;

/** Raised when a share count is not a positive number, or when summing shares would overflow. */
public final class InvalidShareCountException extends DomainException {

    public InvalidShareCountException(String message) {
        super(message);
    }

    public static InvalidShareCountException notPositive(int shares) {
        return new InvalidShareCountException("Share count must be greater than zero but was %d".formatted(shares));
    }

    public static InvalidShareCountException overflow(int current, int additional) {
        return new InvalidShareCountException(
                "Adding %d shares to %d would exceed the maximum supported share count".formatted(additional, current));
    }
}
