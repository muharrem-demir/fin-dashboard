# fin-dashboard

REST API for a financial stock portfolio dashboard. Users create portfolios, manage the stock
holdings inside them, look up live market quotes, and subscribe over a WebSocket to have quotes
pushed to them as they move. There is no authentication by design.

Java 26 · Spring Boot 4.1 · PostgreSQL 18 · Maven

---

## The three rules that matter

**1. Adding a ticker a portfolio already holds increases the existing position — it does not replace
it.** `addStock("AAPL", 10)` followed by `addStock("aapl", 5)` leaves **one** holding of 15 shares,
because `Ticker` normalises to upper case.

This rule lives in `Portfolio.addStock` (`domain/model/Portfolio.java`) and nowhere else. Do not
reimplement it in a service, a SQL `ON CONFLICT`, or a controller. The unique constraint on
`(portfolio_id, ticker)` is a safety net behind it, not the implementation.

**2. Percent change is `(price - previousClose) / previousClose * 100`,** rounded to two decimals,
half up.

This lives in `StockQuote.percentChange()` (`domain/model/StockQuote.java`) and nowhere else — not
in the Yahoo adapter, not in a web mapper. It returns `Optional.empty()` when the previous close is
unknown or zero, because dividing by it is undefined and a fabricated `0.00` would be worse than no
answer. Swapping the quote provider must not be able to change what "percent change" means.

**3. One tick of the live feed is one upstream call, and every subscriber gets only its own
symbols.** A tick fetches the union of every connected client's watchlist once; ten clients all
watching AAPL cost one symbol, not ten.

The union lives in `QuoteSubscriptions.tickers()` and the per-client slice in
`QuoteSubscription.select(...)` (`domain/model/`). `BroadcastQuoteUpdatesUseCase` sequences them
and nothing else. Do not filter quotes per client in the WebSocket handler, the publisher or the
scheduler, and do not give the transport its own idea of who is watching what — the number of
provider calls must stay a function of the tick interval, never of how many clients are connected.

## Architecture

Clean architecture in one Maven module. Dependencies point inward only, and
`CleanArchitectureTest` fails the build if they do not.

```
com.forinvest.dashboard
├── domain/          pure Java — no Spring, no JPA, no Jackson, no Yahoo
│   ├── model/       Portfolio (aggregate root), Holding, Ticker,
│   │                StockQuote, StockQuoteLookup, StockQuoteSnapshot,
│   │                PriceHistory, PricePoint, HistoryWindow, SubscriberId,
│   │                QuoteSubscription, QuoteSubscriptions — all immutable records
│   ├── exception/   sealed DomainException hierarchy
│   └── port/        PortfolioRepository       — the domain's view of storage
│                    StockQuoteProvider        — the domain's view of market data
│                    StockPriceHistoryProvider — the domain's view of past daily closes
│                    QuoteSubscriptionRegistry — who is listening right now
│                    QuoteUpdatePublisher      — pushing one update to one subscriber
├── application/     use cases; depends on domain only
│   ├── usecase/     one class per use case, plain objects with constructor injection
│   ├── command/     input records for writes
│   ├── query/       input records for reads (ListStockQuotesQuery)
│   └── port/        TransactionRunner
└── infrastructure/  every framework lives here, and only here
    ├── persistence/ JPA entities, adapter, mapper — all package-private
    ├── quotes/      Yahoo Finance quote + history adapters, crumb session,
    │                settings — package-private
    ├── subscription/ in-memory QuoteSubscriptionRegistry adapter — package-private
    ├── websocket/   handler, sessions, publisher, scheduler, message DTOs
    ├── web/         controllers, DTOs, GlobalExceptionHandler
    └── config/      Spring wiring
```

### Rules that are mechanically enforced

- `domain` depends on nothing but the JDK. No `@Entity`, no `@Component`, no annotations at all —
  and no `yahoofinance..` either — that rule outlived the library itself and is kept as a guard: a
  market-data client is an infrastructure detail like any other, and `CleanArchitectureTest` will
  fail the build if one is ever reintroduced inward.
- `application` depends on `domain` only. **Use cases carry no Spring annotations** — they are
  registered as beans in `infrastructure/config/UseCaseConfig.java`.
- Transactions do not leak inward. Read-modify-write use cases take the `TransactionRunner` port;
  `SpringTransactionRunner` implements it with a `TransactionTemplate`.
- JPA entities are package-private inside `infrastructure.persistence` and never escape it.
  Everything above the adapter speaks in domain types.
- The web layer goes through use cases, never straight to a repository. So does the WebSocket
  layer: `QuoteStreamHandler` is a transport, held to exactly the rules a controller is.
- `domain` and `application` may not touch `org.springframework.scheduling..` or
  `org.springframework.web.socket..`. When work runs, and how a message reaches a client, are
  infrastructure decisions — enforced by `CleanArchitectureTest`.

### Where to put a new rule

In the domain model — as a method on `Portfolio` returning a new `Portfolio`, or on the relevant
value object (`StockQuote`, `Ticker`, `Holding`, `QuoteSubscription`, `QuoteSubscriptions`). If you
find yourself writing an `if` about portfolio state, a calculation, or a decision about which
client should be sent what, inside a use case, mapper, controller or WebSocket handler, it belongs
in the domain instead.

### Reaching anything outside the process

Define an outbound port in `domain/port/` in domain types, and put the adapter in its own
`infrastructure/` sub-package. `PortfolioRepository` → `infrastructure.persistence`;
`StockQuoteProvider` → `infrastructure.quotes`. The adapter translates the vendor's failures into
domain exceptions so the vendor's types stop at that boundary.

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
| `GET`    | `/stocks/quotes?tickers=AAPL,MSFT`  | batch quotes; `&history=true` adds daily closes; 502 when the provider is down |
| `WS`     | `/ws/quotes`                        | live quotes, pushed every 3 s (not under `/api/v1`) |

Portfolio names are deliberately **not** unique — that was never a requirement.

Every error is an RFC 9457 problem document produced by `GlobalExceptionHandler`. Controllers
contain no error handling. The handler switches exhaustively over the sealed `DomainException`
hierarchy, so **adding a domain exception without mapping it to a status code is a compile error**.

## Browser access (CORS)

One registration in `WebCorsConfig` (`infrastructure/web/`) covers everything under
`dashboard.web.cors.path-pattern` (default `/api/v1/**`). **No `@CrossOrigin` on a controller or a
handler** — an annotation makes the policy a property of whoever remembered to add it, and an
endpoint added later would inherit nothing.

Open by default (`allowed-origins: *`, env `CORS_ALLOWED_ORIGINS`), matching the WebSocket feed:
public market data, no authentication, so an origin restriction here would protect nothing that is
not already public. Credentials are off.

Two details that are decisions, not defaults:

- Origins are registered as **`allowedOriginPatterns`, never `allowedOrigins`**. A literal `*` and
  credentials are illegal together, so the open default would break the moment a deployment turned
  credentials on; a pattern echoes the caller's origin instead and keeps working.
- **`Location` is exposed.** `POST /portfolios` answers 201 with that header and nothing else, and a
  browser cannot read an unexposed header — a cross-origin client would be unable to find the
  portfolio it just created.

CORS does not cover the live feed: a WebSocket handshake is not a CORS request, and its origins are
checked by the handshake itself through `dashboard.quotes.stream.allowed-origins`.

## Market quotes

`GET /api/v1/stocks/quotes?tickers=…` fetches every requested symbol in **one** upstream call —
batching is part of the `StockQuoteProvider` contract, not an optimisation the adapter may skip.
Both `?tickers=A,B` and `?tickers=A&tickers=B` bind. Symbols are de-duplicated case-insensitively,
and at most `ListStockQuotesQuery.MAX_TICKERS` (50) may be requested at once.

Tickers the provider has no data for come back under `unresolved` rather than being dropped, so a
client can tell "no data" from "not asked for".

**A provider failure is a 502, never a 500.** The request was fine; the dependency was not.
`YahooFinanceStockQuoteAdapter` is the anti-corruption layer that translates every upstream failure
— checked or unchecked — into `StockQuoteUnavailableException`.

**Yahoo's quote endpoint requires a cookie and a crumb.** An anonymous `GET /v7/finance/quote` is
answered with HTTP 401. Access is regained by visiting a Yahoo host for a session cookie, then
exchanging that cookie for a short opaque crumb that every quote request carries; the two are a
pair, and a crumb requested without the cookie comes back as `Invalid Cookie`.

`YahooCrumbSession` owns that handshake. The pair is cached — re-fetching it per quote would turn
one upstream call into three — and refreshed only when the quote endpoint rejects it, which the
adapter answers with one fresh handshake and one retry. A crumb expires without warning and an
expired one is indistinguishable from an invalid one, so that retry is what keeps an expiry from
surfacing as an outage.

This is why the adapter calls the endpoint directly instead of using `yahoofinance-api`: that
library issues the quote call unauthenticated, and its crumb support exists only on the
historical-download path where the quote call cannot reach it. The dependency is gone.

The `HttpClient` and its cookie jar are built in `QuoteProviderConfig` and deliberately **not**
exposed as a bean. The jar is the other half of the crumb; one cleared or refilled by unrelated
traffic would invalidate the crumb without the adapter knowing.

Timeout: `dashboard.quotes.yahoo.connection-timeout-millis` (env `QUOTES_TIMEOUT_MS`, default
10 000), applied to the connect and to each request. The endpoints are configurable too
(`quote-url`, `crumb-url`, `cookie-url`, `user-agent`) so a test can point at a stub; they are not
expected to be set in normal operation.

## Historical prices

`GET /api/v1/stocks/quotes?tickers=…&history=true` returns recent daily closes beside the current
quotes. **History is opt-in and its length is not.** How far back it reaches is
`dashboard.quotes.history.days` (env `QUOTES_HISTORY_DAYS`, default 5) — one setting for the whole
deployment, because history costs one upstream call per ticker and how much of it is bought is an
operator's decision, not a caller's.

**Days are trading days, not calendar days.** A five-day window asked for on a Monday answers with
five closes, not the two that fall in the last five calendar days. That rule lives in
`HistoryWindow` (`domain/model/`) and nowhere else: `calendarSpanDays()` sizes the span to ask a
provider for, `mostRecent(...)` cuts what comes back down to the days actually wanted, sorting
oldest-first and collapsing a repeated day on the way. Nothing is padded — a symbol that has traded
for two days has a two-point history, and `days` is reported on every entry so a reader can tell a
short history from a short window.

`StockPriceHistoryProvider` is a **separate port** from `StockQuoteProvider`, and the split is the
whole design:

- The quote port promises one upstream call for the entire batch, and the live feed depends on that
  promise. Yahoo's chart endpoint takes one symbol per request and has no batch form, so history
  cannot make that promise; giving it its own port keeps the quote contract honest. The requests are
  issued concurrently and awaited together, so fifty symbols cost roughly one round trip, not fifty.
- **The streaming path never touches it.** `BroadcastQuoteUpdatesUseCase` talks to
  `StockQuoteProvider` alone, so a tick of the feed sends exactly the request it always did and its
  cost stays a function of the tick interval. `QuoteStreamIT` asserts no `history` key ever appears
  in a pushed frame.

`YahooFinanceStockPriceHistoryAdapter` gets **its own `HttpClient` and cookie jar**, built in
`QuoteProviderConfig` and not exposed as a bean, for the same reason the quote client is not shared:
a chart request that rotated a cookie would invalidate a crumb the quote adapter still believes in,
and the first symptom would be the live feed failing a tick.

Two details in the adapter that are decisions, not defaults:

- **A candle is dated in the exchange's timezone** (`meta.gmtoffset`), not in UTC. Reading a Tokyo
  session in UTC moves it to the previous day and labels the history with dates no Tokyo trader
  recognises.
- **One symbol failing fails the whole call.** A partial answer quietly missing three of five
  symbols is indistinguishable from three symbols with no data, and a client cannot retry what it
  was never told had failed. An unknown symbol (404) is still just omitted — that is missing data,
  not an outage.

History sits **beside** the quotes in the response (`history: [...]`), never inside a
`StockQuoteResponse`. That record is also the live feed's wire format and must not grow a field it
would always send empty. `history` is absent when it was not asked for, and `[]` when it was asked
for and nothing came back — a client can tell "I did not ask" from "there is none", which is the
same distinction `unresolved` draws for quotes.

## Realtime quote streaming

`ws://<host>/ws/quotes`. Raw WebSocket, no STOMP and no broker: each client keeps its own
connection and its own watchlist, so there is nothing for a destination hierarchy to do.

Client → server, and nothing else:

```json
{"action": "subscribe", "tickers": ["AAPL", "MSFT"]}
{"action": "unsubscribe"}
```

Server → client, discriminated by `type`: `connected`, `subscribed`, `unsubscribed`, `quotes`,
`error` (the client's mistake), `unavailable` (the provider's). Quotes are serialised by
`StockQuoteWebMapper` — **the same mapper the REST endpoint uses**, so a quote has one wire shape
whatever transport delivered it.

How the pieces fit:

```
QuoteStreamHandler   client messages  → SubscribeToQuotesUseCase / UnsubscribeFromQuotesUseCase
QuoteBroadcastScheduler  every tick   → BroadcastQuoteUpdatesUseCase
                                         → StockQuoteProvider (one call for the union)
                                         → QuoteUpdatePublisher (one message per subscriber)
QuoteStreamSessions  the open connections; the only place anything is written to one
```

Rules to keep:

- **The subscriber id is the WebSocket session id.** That is what makes every connection an
  independent client without anybody having to authenticate or invent an identity.
- **Subscribing replaces.** Changing symbols is the same call as subscribing; there is no merge and
  no unsubscribe-first step. `QuoteSubscriptionRegistry.save` is a `put`, never a `merge`.
- **Unsubscribing is idempotent**, because an explicit unsubscribe races with a disconnect.
- **No message closes the connection.** An invalid symbol is an `error` frame and the previous
  subscription survives; a provider outage is an `unavailable` frame and the next tick is the retry.
- **Nothing is fetched while nobody is subscribed.** `BroadcastQuoteUpdatesUseCase` returns early on
  an empty registry, which is also why an idle test context never calls Yahoo.
- **`fixedDelay`, not `fixedRate`**, so a slow upstream call delays the next tick instead of
  stacking concurrent requests on the provider.
- **Every send goes through `QuoteStreamSessions`**, which wraps each session in a
  `ConcurrentWebSocketSessionDecorator`. Two threads write to a session — the container thread
  answering a client and the scheduler thread pushing a tick — and a raw `WebSocketSession` is not
  safe for that. A failed send drops that one session and is never rethrown.

Settings live under `dashboard.quotes.stream` (`path`, `interval-millis`, `allowed-origins`,
`send-time-limit-millis`, `send-buffer-size-bytes`). The scheduler resolves `interval-millis`
itself through a property placeholder, because a `@Scheduled` annotation cannot read a bound
record.

Subscriptions are in memory, which is correct for one instance. Running several would want a shared
`QuoteSubscriptionRegistry` implementation — one class, and nothing above the port changes.

## Database

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate`, so a migration that drifts from the entity mapping fails at startup.

Never edit an applied migration — add a new `V{n}__description.sql`.

Note: Spring Boot 4 splits auto-configuration per technology. Flyway needs
`spring-boot-starter-flyway`; `flyway-core` alone is just the library and silently does nothing.

## Testing

| Layer                       | Style                                       | Docker |
| --------------------------- | ------------------------------------------- | ------ |
| `domain/`                   | plain JUnit, no mocks                       | no     |
| `application/usecase/`      | Mockito on the ports                        | no     |
| `infrastructure/web/`       | `@WebMvcTest` with mocked use cases         | no     |
| `infrastructure/quotes/`    | stub HTTP server on loopback                | no     |
| `infrastructure/websocket/` | Mockito on the use cases and the session    | no     |
| `integration/`              | `@SpringBootTest` + real PostgreSQL         | **yes**|
| `architecture/`             | ArchUnit                                    | no     |

**No test ever calls Yahoo.** A third party rate-limiting us must not fail the build, and live
market data is not deterministic enough to assert on. `StockQuoteApiIT` and `QuoteStreamIT` replace
the `StockQuoteProvider` bean with `@MockitoBean` — and `StockQuoteApiIT` replaces
`StockPriceHistoryProvider` too, which is what keeps a `history=true` request from fanning out to
Yahoo one call per ticker; `YahooFinanceStockQuoteAdapterTest` runs the real
adapter against a `com.sun.net.httpserver.HttpServer` stub on loopback that imitates the cookie,
crumb and quote endpoints, and `YahooFinanceStockPriceHistoryAdapterTest` does the same for the
chart endpoint. A stub rather than a mocked `HttpClient`, because the part most likely to
break is the handshake itself, and canned responses would only prove we wrote the code we wrote. The
only untested link is the live Yahoo call itself.

`QuoteStreamIT` opens real WebSocket connections to a real server (`RANDOM_PORT` +
`StandardWebSocketClient`) and shortens the tick to 200 ms. Two rules keep it from flaking: the
provider mock is stubbed **once** with an answer the test steers through an `AtomicReference` —
re-stubbing a mock the scheduler is calling on another thread is a race — and `@AfterEach` closes
every client and waits for `QuoteSubscriptionRegistry.current()` to drain, so the next test starts
with a feed that is asking for nothing.

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
- `yahoofinance-api` was dropped (see "Market quotes"). It also pinned SLF4J 1.x, which would shadow
  Boot's 2.x binding and silently disable logging; that exclusion went with it. Watch for the same
  trap in any library added to replace it.
- Jackson 3's exceptions are unchecked: `JacksonException extends RuntimeException`, so reading or
  writing JSON by hand needs no `throws`. Boot auto-configures a `tools.jackson.databind.json.JsonMapper`
  bean — inject that, not an `ObjectMapper`, and the stream gets the same date and inclusion
  settings as the REST API.
- Adding a dependency can leave `target/` in a state where incremental `test-compile` cannot resolve
  the new library from already-compiled main classes ("class file for WebSocketSession not found").
  `./mvnw clean test-compile` clears it; the build itself is fine.

## Conventions

- Records for anything immutable; validation in the compact constructor so an invalid instance
  cannot exist.
- Constructor injection only. No field injection, no `@Autowired` on fields.
- Package-private by default — widen only when something outside the package genuinely needs it.
- Comments explain *why*, not *what*. The build rejects `TODO` and `FIXME`; fix it or file an issue.
