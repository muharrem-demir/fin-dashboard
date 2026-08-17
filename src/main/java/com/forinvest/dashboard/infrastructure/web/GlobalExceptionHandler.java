package com.forinvest.dashboard.infrastructure.web;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.forinvest.dashboard.domain.exception.DomainException;
import com.forinvest.dashboard.domain.exception.InvalidPortfolioNameException;
import com.forinvest.dashboard.domain.exception.InvalidQuoteRequestException;
import com.forinvest.dashboard.domain.exception.InvalidShareCountException;
import com.forinvest.dashboard.domain.exception.InvalidTickerException;
import com.forinvest.dashboard.domain.exception.PortfolioNotFoundException;
import com.forinvest.dashboard.domain.exception.StockNotFoundException;
import com.forinvest.dashboard.domain.exception.StockQuoteUnavailableException;

/**
 * Translates every failure into an RFC 9457 problem document.
 *
 * <p>Controllers therefore contain no error handling at all, and clients get one predictable error
 * shape — {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * {@code timestamp} — whether the failure came from validation, the domain or the database.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://api.forinvest.com/problems/";

    /**
     * Maps the domain's failures to HTTP.
     *
     * <p>The switch is exhaustive over the sealed {@link DomainException} hierarchy, so adding a new
     * domain exception without deciding its status code is a compile error rather than an
     * accidental 500 in production.
     */
    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException exception, HttpServletRequest request) {
        ProblemDetail problem =
                switch (exception) {
                    case PortfolioNotFoundException e ->
                        problem(HttpStatus.NOT_FOUND, "Portfolio not found", "portfolio-not-found", e.getMessage());
                    case StockNotFoundException e ->
                        problem(HttpStatus.NOT_FOUND, "Stock not found", "stock-not-found", e.getMessage());
                    case InvalidTickerException e ->
                        problem(HttpStatus.BAD_REQUEST, "Invalid ticker", "invalid-ticker", e.getMessage());
                    case InvalidShareCountException e ->
                        problem(HttpStatus.BAD_REQUEST, "Invalid share count", "invalid-share-count", e.getMessage());
                    case InvalidPortfolioNameException e ->
                        problem(
                                HttpStatus.BAD_REQUEST,
                                "Invalid portfolio name",
                                "invalid-portfolio-name",
                                e.getMessage());
                    case InvalidQuoteRequestException e ->
                        problem(
                                HttpStatus.BAD_REQUEST,
                                "Invalid quote request",
                                "invalid-quote-request",
                                e.getMessage());
                    // The caller did nothing wrong — the market data provider did. 502, not 500.
                    case StockQuoteUnavailableException e ->
                        problem(
                                HttpStatus.BAD_GATEWAY,
                                "Stock quotes unavailable",
                                "stock-quotes-unavailable",
                                e.getMessage());
                };
        return withRequestContext(problem, request);
    }

    /** A path variable that could not be bound, most often a malformed portfolio UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String detail =
                "Parameter '%s' has an invalid value: '%s'".formatted(exception.getName(), exception.getValue());
        return withRequestContext(
                problem(HttpStatus.BAD_REQUEST, "Invalid request parameter", "invalid-parameter", detail), request);
    }

    /**
     * A database constraint rejected the write.
     *
     * <p>Reaching here means an invariant slipped past the domain — the unique holding constraint,
     * for example — so it is logged at warn level as a signal that something upstream is wrong.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        LOG.warn("Database constraint rejected a write on {}", request.getRequestURI(), exception);
        return withRequestContext(
                problem(
                        HttpStatus.CONFLICT,
                        "Conflicting request",
                        "conflict",
                        "The request conflicts with the current state of the data."),
                request);
    }

    /**
     * Anything unanticipated.
     *
     * <p>The client gets an opaque message plus a correlation id; the stack trace stays in the
     * server log, where it belongs. Leaking exception text here is how internals and connection
     * strings end up in bug reports.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        LOG.error(
                "Unhandled exception [errorId={}] on {} {}",
                errorId,
                request.getMethod(),
                request.getRequestURI(),
                exception);

        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "internal-error",
                "The request could not be completed. Quote errorId when reporting this.");
        problem.setProperty("errorId", errorId);
        return withRequestContext(problem, request);
    }

    /** Bean-validation failures, reported field by field so a client can highlight the inputs. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), messageOf(error)))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed", "One or more fields are invalid.");
        problem.setProperty("errors", violations);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.badRequest().body(problem);
    }

    private static String messageOf(FieldError error) {
        return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + type));
        return problem;
    }

    private static ProblemDetail withRequestContext(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** One invalid field, as it appears in the {@code errors} array of a validation problem. */
    record FieldViolation(String field, String message) {}
}
