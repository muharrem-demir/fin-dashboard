# fin-dashboard

REST API for a financial stock portfolio dashboard. Create portfolios, add or remove the stock
holdings inside them, and look up live market quotes with their percent change — or subscribe to a
WebSocket and have them pushed to you as they move.

**Java 26 · Spring Boot 4.1 · PostgreSQL 18 · Maven**

No authentication — the API is open by design.

## AI Assistance

Claude Code ise used for developing this project.

In the beginning of the development process CLAUDE.md file and .claude folder contents (skills, agents) are created. This provided consistency during adding new features to the project.

As a first step, project's techical requirements are specified. Necessary features are developed by the AI agents, after carefully describing the requirements.

The changes and fixes related to the existing features are also developed by AI agents, again by clearly promtping the requirements.

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

## The rules worth knowing

### Adding shares accumulates

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

### Percent change

`(price - previousClose) / previousClose * 100`, rounded to two decimals. It is omitted entirely
when the previous close is unknown or zero, rather than reported as a misleading `0.00`.

## Market quotes

Quote several tickers in a **single** upstream call:

```bash
curl -s 'http://localhost:8080/api/v1/stocks/quotes?tickers=AAPL,MSFT,NOSUCH' | jq
```

```json
{
  "quotes": [
    { "ticker": "AAPL", "price": 150.25, "previousClose": 148.50, "percentChange": 1.18 },
    { "ticker": "MSFT", "price": 198.00, "previousClose": 200.00, "percentChange": -1.00 }
  ],
  "unresolved": ["NOSUCH"],
  "quoteCount": 2
}
```

Symbols are matched case-insensitively and de-duplicated, up to 50 per request. Both
`?tickers=A,B` and `?tickers=A&tickers=B` work. Tickers the provider has no data for are listed
under `unresolved` instead of being silently dropped.

> **Heads up.** Quotes come from Yahoo Finance via
> [`sstrickx/yahoofinance-api`](https://github.com/sstrickx/yahoofinance-api). Yahoo's public quote
> endpoint now rejects unauthenticated callers (HTTP 401/429), so this endpoint may return **502
> Bad Gateway** against the live service until credentialed access is configured. The integration
> itself is complete and fully tested; swapping in a different provider means writing one new
> adapter for the `StockQuoteProvider` port — no domain, use case or controller changes.

## Live quotes over WebSocket

Connect to `ws://localhost:8080/ws/quotes`, say what you want, and quotes are pushed to you every
three seconds until you say otherwise.

```bash
websocat ws://localhost:8080/ws/quotes
```

```json
< {"type":"connected","subscriberId":"3f8c1a2b","intervalMillis":3000}
> {"action":"subscribe","tickers":["AAPL","MSFT"]}
< {"type":"subscribed","tickers":["AAPL","MSFT"]}
< {"type":"quotes","timestamp":"2026-08-18T09:14:02.117Z","quotes":[
    {"ticker":"AAPL","price":150.25,"previousClose":148.50,"percentChange":1.18},
    {"ticker":"MSFT","price":198.00,"previousClose":200.00,"percentChange":-1.00}],
   "unresolved":[],"quoteCount":2}
```

A quote has the same shape here as it does over HTTP — same fields, same percent-change rule.

### What a client can say

| Message                                          | Effect                                         |
| ------------------------------------------------ | ---------------------------------------------- |
| `{"action":"subscribe","tickers":["AAPL"]}`       | watch exactly these symbols, **replacing** any |
| `{"action":"unsubscribe"}`                        | stop updates, keep the connection open         |

**Symbols can be changed at any time**: send `subscribe` again and the next tick follows the new
list. There is no unsubscribe-then-resubscribe dance, and nothing arrives for a symbol you have
already dropped.

### What the server sends

| `type`        | Meaning                                                                  |
| ------------- | ------------------------------------------------------------------------ |
| `connected`   | your subscriber id and how often updates will arrive                     |
| `subscribed`  | what you are now watching, normalised and de-duplicated                  |
| `unsubscribed`| updates stopped                                                          |
| `quotes`      | one tick of prices for your symbols, plus `unresolved` for ones with no data |
| `error`       | your message was wrong — an invalid symbol, an unknown action            |
| `unavailable` | the quote provider is down; this is the streaming counterpart of the 502 |

Neither `error` nor `unavailable` closes the connection, and neither discards what you were
watching: a bad symbol leaves your previous subscription intact, and the next tick is the retry.

### How it behaves

- **Every connection is its own client** with its own watchlist. Two browser tabs are two
  subscribers, and neither sees the other's symbols.
- **One tick, one upstream call.** Each tick fetches the *union* of every connected client's
  symbols in a single provider call and gives each client its own slice, so ten clients all
  watching AAPL cost one symbol, not ten.
- **Nothing is fetched while nobody is subscribed** — an idle instance makes no upstream calls at
  all.
- At most 50 symbols per subscription, matched case-insensitively and de-duplicated, exactly as in
  the REST batch.
- Subscriptions live only as long as the connection: a disconnect unsubscribes.

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
| `GET`    | `/stocks/quotes?tickers=…`         | 200 — batch quotes; 502 if provider down  |
| `WS`     | `/ws/quotes`                       | live quotes, pushed every 3 s             |

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
are tested with plain JUnit and no Spring context. The percent-change formula lives in the
`StockQuote` value object for the same reason.

Everything outside the process is reached through a port declared in the domain and implemented in
`infrastructure`: `PortfolioRepository` → `infrastructure.persistence` (JPA),
`StockQuoteProvider` → `infrastructure.quotes` (Yahoo Finance), `QuoteUpdatePublisher` and
`QuoteSubscriptionRegistry` → the WebSocket feed. Even transactions stay out of the inner layers,
behind a `TransactionRunner` port. The Yahoo library is barred from `domain` and `application` by
an ArchUnit rule, so the market-data vendor can be replaced without touching a business rule; so
are Spring's scheduling and WebSocket packages, which is why the streaming rules — who is watching
what, what one tick must fetch, which client gets which quotes — are plain records tested with
plain JUnit, and the WebSocket handler decides nothing.

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

No test calls Yahoo: the quote provider is stubbed at the port, and the adapter is tested against a
mocked library entry point. A build never fails because a third party rate-limited it. The live
feed is covered the same way — `QuoteStreamIT` opens real WebSocket connections to a real server
and asserts on the pushed frames, with only the provider replaced.

## Configuration

Profiles: `local` (default, localhost Postgres), `dev` (env vars with compose-friendly defaults),
`prod` (env vars with **no** defaults — a missing credential stops startup rather than silently
falling back). Copy `.env.example` to `.env` to override compose settings.

Quote-provider timeout: `dashboard.quotes.yahoo.connection-timeout-millis`, or the
`QUOTES_TIMEOUT_MS` environment variable (default 10 000 ms).

The live feed is configured under `dashboard.quotes.stream`:

| Property                  | Env var                         | Default      | Meaning                                  |
| ------------------------- | ------------------------------- | ------------ | ---------------------------------------- |
| `path`                    | —                               | `/ws/quotes` | where clients connect                    |
| `interval-millis`         | `QUOTES_STREAM_INTERVAL_MS`     | `3000`       | how often quotes are fetched and pushed  |
| `allowed-origins`         | `QUOTES_STREAM_ALLOWED_ORIGINS` | `*`          | origin patterns allowed to connect       |
| `send-time-limit-millis`  | —                               | `5000`       | a client slower than this is disconnected |
| `send-buffer-size-bytes`  | —                               | `524288`     | how much may queue for one slow client   |

The interval is the gap *between* ticks: a tick starts once the previous one has finished, so a
slow upstream call delays the next update instead of stacking concurrent requests on the provider.

Browser access is configured under `dashboard.web.cors`:

| Property           | Env var                  | Default                        | Meaning                            |
| ------------------ | ------------------------ | ------------------------------ | ---------------------------------- |
| `path-pattern`     | —                        | `/api/v1/**`                   | the paths CORS applies to          |
| `allowed-origins`  | `CORS_ALLOWED_ORIGINS`   | `*`                            | origin patterns allowed to call it |
| `allowed-methods`  | —                        | `GET,POST,PATCH,DELETE,OPTIONS`| methods a browser may use          |
| `allowed-headers`  | —                        | `*`                            | request headers a browser may send |
| `exposed-headers`  | —                        | `Location`                     | response headers a browser may read |
| `allow-credentials`| `CORS_ALLOW_CREDENTIALS` | `false`                        | whether cookies may be sent        |
| `max-age-seconds`  | —                        | `3600`                         | how long a preflight answer is cached |

Set once in `WebCorsConfig`, never with `@CrossOrigin` on a controller — one answer to "who may call
this API", and an endpoint added tomorrow is covered without anyone remembering to annotate it.
`Location` is exposed because creating a portfolio answers 201 with that header and nothing else,
and a browser cannot read a header that is not exposed. The WebSocket handshake is not a CORS
request; its origins are the separate `dashboard.quotes.stream.allowed-origins`.

The schema is owned by Flyway (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate`, so a migration that drifts from the entity mapping fails at startup.

See [CLAUDE.md](CLAUDE.md) for the architecture rules in detail.
