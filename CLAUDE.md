# fin-dashboard

REST API for a financial stock portfolio dashboard. Users create portfolios and manage the stock
holdings inside them. There is no authentication by design.

Java 26 · Spring Boot 4.1 · PostgreSQL 18 · Maven

---

## The rule that matters

**Adding a ticker a portfolio already holds increases the existing position — it does not replace
it.** `addStock("AAPL", 10)` followed by `addStock("aapl", 5)` leaves **one** holding of 15 shares,
because `Ticker` normalises to upper case.

This rule lives in `Portfolio.addStock` (`domain/model/Portfolio.java`) and nowhere else. Do not
reimplement it in a service, a SQL `ON CONFLICT`, or a controller. The unique constraint on
`(portfolio_id, ticker)` is a safety net behind it, not the implementation.

## Architecture

Clean architecture in one Maven module. Dependencies point inward only, and
`CleanArchitectureTest` fails the build if they do not.

```
com.forinvest.dashboard
├── domain/          pure Java — no Spring, no JPA, no Jackson
│   ├── model/       Portfolio (aggregate root), Holding, Ticker — all immutable records
│   ├── exception/   sealed DomainException hierarchy
│   └── port/        PortfolioRepository — the domain's view of storage
├── application/     use cases; depends on domain only
│   ├── usecase/     one class per use case, plain objects with constructor injection
│   ├── command/     input records
│   └── port/        TransactionRunner
└── infrastructure/  every framework lives here, and only here
    ├── persistence/ JPA entities, adapter, mapper — all package-private
    ├── web/         controllers, DTOs, GlobalExceptionHandler
    └── config/      Spring wiring
```

### Rules that are mechanically enforced

- `domain` depends on nothing but the JDK. No `@Entity`, no `@Component`, no annotations at all.
- `application` depends on `domain` only. **Use cases carry no Spring annotations** — they are
  registered as beans in `infrastructure/config/UseCaseConfig.java`.
- Transactions do not leak inward. Read-modify-write use cases take the `TransactionRunner` port;
  `SpringTransactionRunner` implements it with a `TransactionTemplate`.
- JPA entities are package-private inside `infrastructure.persistence` and never escape it.
  Everything above the adapter speaks in domain types.
- The web layer goes through use cases, never straight to a repository.

### Where to put a new rule

In the domain model, as a method on `Portfolio` that returns a new `Portfolio`. If you find
yourself writing an `if` about portfolio state inside a use case or a controller, it belongs in the
aggregate instead.

## Commands

```bash
./mvnw verify                  # the whole gate: format, lint, tests, ITs, bugs, coverage
./mvnw spotless:apply          # auto-fix formatting — run this before committing
./mvnw test                    # unit tests only, no Docker needed
./mvnw verify -DskipITs        # skip the Testcontainers integration tests

docker compose up -d postgres  # database only, then run the app from your IDE
docker compose up -d --build   # the whole stack
```

Local run: `./mvnw spring-boot:run` uses the `local` profile and expects Postgres on
`localhost:5432` (start it with the compose command above).

- Swagger UI — <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON — <http://localhost:8080/v3/api-docs>
- Health — <http://localhost:8080/actuator/health>

## API

Base path `/api/v1`.

| Method   | Path                                | Notes                                          |
| -------- | ----------------------------------- | ---------------------------------------------- |
| `GET`    | `/portfolios`                       | summaries, no holdings                         |
| `POST`   | `/portfolios`                       | 201 + `Location`                               |
| `GET`    | `/portfolios/{id}`                  | full portfolio                                 |
| `PATCH`  | `/portfolios/{id}`                  | rename                                         |
| `DELETE` | `/portfolios/{id}`                  | cascades to holdings                           |
| `POST`   | `/portfolios/{id}/stocks`           | **adds to** an existing position               |
| `DELETE` | `/portfolios/{id}/stocks/{ticker}`  | 404 if the ticker is not held                  |

Portfolio names are deliberately **not** unique — that was never a requirement.

Every error is an RFC 9457 problem document produced by `GlobalExceptionHandler`. Controllers
contain no error handling. The handler switches exhaustively over the sealed `DomainException`
hierarchy, so **adding a domain exception without mapping it to a status code is a compile error**.

## Database

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate`, so a migration that drifts from the entity mapping fails at startup.

Never edit an applied migration — add a new `V{n}__description.sql`.

Note: Spring Boot 4 splits auto-configuration per technology. Flyway needs
`spring-boot-starter-flyway`; `flyway-core` alone is just the library and silently does nothing.

## Testing

| Layer                    | Style                                    | Docker |
| ------------------------ | ---------------------------------------- | ------ |
| `domain/`                | plain JUnit, no mocks                    | no     |
| `application/usecase/`   | Mockito on the repository port           | no     |
| `infrastructure/web/`    | `@WebMvcTest` with mocked use cases      | no     |
| `integration/`           | `@SpringBootTest` + real PostgreSQL      | **yes**|
| `architecture/`          | ArchUnit                                 | no     |

`*Test` runs under Surefire, `*IT` under Failsafe. Integration tests use Testcontainers via
`@ServiceConnection`, so no URL or credentials are configured anywhere.

Use `DirectTransactionRunner` (a pass-through fake) rather than mocking `TransactionRunner`.

## Quality gate

`./mvnw verify` fails on any of: Spotless formatting, Checkstyle, SpotBugs, JaCoCo coverage below
80% line / 70% branch on `domain` + `application`, ArchUnit, or any test.

All of it runs on JDK 26 with no fallbacks or downgrades — verified, not assumed.

Configuration lives in `config/checkstyle/` and `config/spotbugs/`. Suppressions are narrow and
each carries a comment explaining why; prefer fixing the code over widening them.

`.mvn/jvm.config` supplies the `--add-exports` flags palantir-java-format needs to read `javac`
internals on JDK 16+. Removing it breaks `spotless`.

## Spring Boot 4 gotchas hit while building this

These cost time once; they are written down so they do not cost it again.

- Test slices moved out of `spring-boot-starter-test` into per-technology starters:
  `@WebMvcTest` needs `spring-boot-starter-webmvc-test` and now lives in
  `org.springframework.boot.webmvc.test.autoconfigure`.
- Jackson 3 (`tools.jackson.*`) replaced Jackson 2. `WRITE_DATES_AS_TIMESTAMPS` moved from
  `SerializationFeature` to `DateTimeFeature`, so the property is
  `spring.jackson.datatype.datetime.write-dates-as-timestamps`.
- Testcontainers 2.x renamed every module (`testcontainers-postgresql`, not `postgresql`), moved
  `PostgreSQLContainer` to `org.testcontainers.postgresql`, and dropped its self-type generic —
  write `PostgreSQLContainer`, not `PostgreSQLContainer<?>`.

## Conventions

- Records for anything immutable; validation in the compact constructor so an invalid instance
  cannot exist.
- Constructor injection only. No field injection, no `@Autowired` on fields.
- Package-private by default — widen only when something outside the package genuinely needs it.
- Comments explain *why*, not *what*. The build rejects `TODO` and `FIXME`; fix it or file an issue.
