---
name: add-use-case
description: Add a new use case to fin-dashboard without breaking the clean-architecture boundaries. Use when adding any new API operation, business rule, or endpoint — for example "let users move shares between portfolios", "add an endpoint to clear a portfolio", or "support setting shares to an exact value".
---

# Adding a use case

Work outward from the domain. Each step is testable before the next one exists, and the order is
what keeps business rules from drifting into the framework.

## 1. Decide where the rule lives

If the operation involves any decision about portfolio state — what happens when the ticker is
already held, whether a position may go to zero, what counts as valid — **that decision belongs in
`Portfolio`**, as a method returning a new `Portfolio`.

A use case may only sequence: load, call one domain method, save. If you are writing an `if` about
portfolio state inside a use case, move it into the aggregate.

## 2. Domain (`domain/model/Portfolio.java`)

Add the method. Return a new instance; never mutate. Throw a `DomainException` subtype for invalid
input — and if you need a new one, add it to the `permits` clause of the sealed `DomainException`.
That deliberately breaks the exhaustive switch in `GlobalExceptionHandler` until you map it to a
status code.

Write the domain test first (`src/test/java/.../domain/model/PortfolioTest.java`). Plain JUnit, no
mocks, no Spring. If the rule is hard to test here, it is in the wrong place.

## 3. Command (`application/command/`)

A record of primitives and `UUID`s. Keep the ticker a `String` — turning it into a `Ticker` is the
domain's job, so an invalid symbol is reported as a domain error.

```java
public record MoveSharesCommand(UUID fromPortfolioId, UUID toPortfolioId, String ticker, int shares) {}
```

## 4. Use case (`application/usecase/`)

One class, one public `execute` method, `final`, constructor-injected with the **port**
(`PortfolioRepository`) — never a Spring Data repository.

**No Spring annotations.** Not `@Service`, not `@Transactional`. `CleanArchitectureTest` fails the
build if you add one.

For read-modify-write, take `TransactionRunner` and wrap the body:

```java
public Portfolio execute(MoveSharesCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Ticker ticker = Ticker.of(command.ticker());
    return transactionRunner.inTransaction(() -> {
        Portfolio portfolio = portfolioRepository
                .findById(command.portfolioId())
                .orElseThrow(() -> new PortfolioNotFoundException(command.portfolioId()));
        return portfolioRepository.save(portfolio.someDomainMethod(ticker, command.shares()));
    });
}
```

A single repository call needs no `TransactionRunner`.

Test it with Mockito on `PortfolioRepository` plus `new DirectTransactionRunner()`. Assert what was
saved with an `ArgumentCaptor`, and that nothing is saved on the failure paths.

## 5. Wire it (`infrastructure/config/UseCaseConfig.java`)

Add a `@Bean` method. This is the only place the use case meets Spring.

## 6. Web (`infrastructure/web/`)

- Request/response records in `dto/`, with `jakarta.validation` constraints and `@Schema`
  descriptions — the OpenAPI document is generated from these.
- Add the endpoint to `PortfolioController` (portfolio itself) or `PortfolioStockController`
  (holdings). If a controller passes ~6 constructor parameters, split it by resource rather than
  suppressing the Checkstyle warning.
- **No try/catch.** Let exceptions reach `GlobalExceptionHandler`.
- Annotate with `@Operation` and `@ApiResponses` including the error codes.

Test with `@WebMvcTest` + `@MockitoBean` on the use cases: status codes, JSON shape, validation
errors, problem documents.

## 7. Migration, if the schema changes

New file `src/main/resources/db/migration/V{n}__description.sql`. Never edit an applied migration.
`ddl-auto: validate` means a mismatch with the entities fails startup.

## 8. Integration test (`integration/PortfolioApiIT.java`)

Add a case that exercises the operation over HTTP against real PostgreSQL.

## 9. Verify

```bash
./mvnw spotless:apply && ./mvnw verify
```

Fix violations at the source rather than widening a suppression.
