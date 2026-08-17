# fin-dashboard

REST API for a financial stock portfolio dashboard. Create portfolios, and add or remove the stock
holdings inside them.

**Java 26 · Spring Boot 4.1 · PostgreSQL 18 · Maven**

No authentication — the API is open by design.

## Quick start

```bash
docker compose up -d --build
curl -s http://localhost:8080/actuator/health
```

Then open <http://localhost:8080/swagger-ui.html>.

To develop with a faster restart loop, run only the database in Docker and the app from Maven:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

## The one rule worth knowing

Adding a ticker a portfolio already holds **increases** the existing position rather than replacing
it, and ticker matching is case-insensitive:

```bash
BASE=http://localhost:8080/api/v1
ID=$(curl -sX POST $BASE/portfolios -H 'Content-Type: application/json' -d '{"name":"Growth"}' | jq -r .id)

curl -sX POST $BASE/portfolios/$ID/stocks -H 'Content-Type: application/json' -d '{"ticker":"AAPL","shares":10}' >/dev/null
curl -sX POST $BASE/portfolios/$ID/stocks -H 'Content-Type: application/json' -d '{"ticker":"aapl","shares":5}'  | jq '.stocks'
```

```json
[ { "ticker": "AAPL", "shares": 15 } ]
```

One holding, 15 shares.

## API

Base path `/api/v1`.

| Method   | Path                               | Result                                    |
| -------- | ---------------------------------- | ----------------------------------------- |
| `GET`    | `/portfolios`                      | 200 — summaries without holdings          |
| `POST`   | `/portfolios`                      | 201 + `Location`                          |
| `GET`    | `/portfolios/{id}`                 | 200 — full portfolio                      |
| `PATCH`  | `/portfolios/{id}`                 | 200 — rename                              |
| `DELETE` | `/portfolios/{id}`                 | 204 — cascades to holdings                |
| `POST`   | `/portfolios/{id}/stocks`          | 200 — adds to any existing position       |
| `DELETE` | `/portfolios/{id}/stocks/{ticker}` | 204, or 404 if the ticker is not held     |

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem documents:

```json
{
  "type": "https://api.forinvest.com/problems/portfolio-not-found",
  "title": "Portfolio not found",
  "status": 404,
  "detail": "Portfolio 3f8c… was not found",
  "instance": "/api/v1/portfolios/3f8c…",
  "timestamp": "2026-08-17T19:22:31.284Z"
}
```

Validation failures add an `errors` array of `{field, message}`.

The OpenAPI document is generated from the code and served at `/v3/api-docs`; CI publishes it as a
build artifact.

## Architecture

Clean architecture in a single Maven module. Dependencies point inward only, and **ArchUnit fails
the build** if they do not.

```
domain/          pure Java — business rules, no framework at all
application/     use cases; depends on domain only
infrastructure/  Spring, JPA, HTTP — the only layer that knows a framework exists
```

`Portfolio` is an immutable aggregate root holding every portfolio rule, which is why those rules
are tested with plain JUnit and no Spring context. Storage is reached through the
`PortfolioRepository` port; `infrastructure.persistence` supplies the JPA adapter. Even
transactions stay out of the inner layers, behind a `TransactionRunner` port.

## Development

```bash
./mvnw verify           # format, lint, unit tests, integration tests, bug analysis, coverage
./mvnw spotless:apply   # auto-fix formatting
./mvnw test             # unit tests only — no Docker required
```

`./mvnw verify` fails on any of: Spotless formatting, Checkstyle, SpotBugs, coverage below 80%
line / 70% branch on `domain` + `application`, an ArchUnit rule, or a failing test.

Integration tests run against a real PostgreSQL through Testcontainers, so **Docker must be
running** for `verify`. They exercise the Flyway migrations and the JPA mapping together.

## Configuration

Profiles: `local` (default, localhost Postgres), `dev` (env vars with compose-friendly defaults),
`prod` (env vars with **no** defaults — a missing credential stops startup rather than silently
falling back). Copy `.env.example` to `.env` to override compose settings.

The schema is owned by Flyway (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate`, so a migration that drifts from the entity mapping fails at startup.

See [CLAUDE.md](CLAUDE.md) for the architecture rules in detail.
