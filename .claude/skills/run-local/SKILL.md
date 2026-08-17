---
name: run-local
description: Start fin-dashboard locally and exercise the API by hand. Use when asked to run, start, or demo the app, to check an endpoint against a real database, or to reproduce a bug end to end.
---

# Running fin-dashboard locally

Two ways. Prefer the first while developing — restarts are much faster.

## App from Maven, database in Docker

```bash
docker compose up -d postgres
docker compose ps                     # wait for "healthy"
./mvnw spring-boot:run                # uses the `local` profile
```

The `local` profile expects Postgres on `localhost:5432` with database `fin_dashboard` and
`dashboard`/`dashboard`, which is exactly what the compose service provides. Flyway migrates at
startup.

## Whole stack in Docker

```bash
docker compose up -d --build
docker compose logs -f api
```

Runs the `dev` profile. Rebuild after code changes: `docker compose up -d --build api`.

## Endpoints

- Swagger UI — <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON — <http://localhost:8080/v3/api-docs>
- Health — <http://localhost:8080/actuator/health>

## Exercising the API

```bash
BASE=http://localhost:8080/api/v1

ID=$(curl -sX POST $BASE/portfolios -H 'Content-Type: application/json' \
      -d '{"name":"Growth"}' | jq -r .id)

curl -sX POST $BASE/portfolios/$ID/stocks -H 'Content-Type: application/json' \
      -d '{"ticker":"AAPL","shares":10}' | jq
curl -sX POST $BASE/portfolios/$ID/stocks -H 'Content-Type: application/json' \
      -d '{"ticker":"aapl","shares":5}'  | jq '.stocks'
# -> one AAPL holding of 15 shares: the additive-upsert rule

curl -s $BASE/portfolios | jq
curl -sX PATCH $BASE/portfolios/$ID -H 'Content-Type: application/json' -d '{"name":"Income"}' | jq
curl -sX DELETE $BASE/portfolios/$ID/stocks/AAPL -o /dev/null -w '%{http_code}\n'   # 204
curl -sX DELETE $BASE/portfolios/$ID/stocks/AAPL -o /dev/null -w '%{http_code}\n'   # 404
curl -sX DELETE $BASE/portfolios/$ID -o /dev/null -w '%{http_code}\n'               # 204
```

Error shapes (RFC 9457 problem documents):

```bash
curl -s $BASE/portfolios/00000000-0000-0000-0000-000000000000 | jq   # 404
curl -sX POST $BASE/portfolios -H 'Content-Type: application/json' -d '{"name":""}' | jq  # 400 + errors[]
```

## Market quotes

```bash
curl -s "$BASE/stocks/quotes?tickers=AAPL,MSFT,NOSUCH" | jq
curl -s "$BASE/stocks/quotes?tickers=AAPL&tickers=MSFT" | jq   # repeated params also work
curl -s "$BASE/stocks/quotes?tickers=1BAD!" | jq               # 400 invalid-ticker
```

Expect `quotes[]` with `price` and `percentChange`, plus `unresolved[]` for symbols the provider
had no data for.

**A 502 here is expected, not a bug in the app.** Yahoo's public quote endpoint rejects
unauthenticated callers (HTTP 401/429), so the live call often fails:

```json
{ "type": "https://api.forinvest.com/problems/stock-quotes-unavailable", "status": 502 }
```

Check `docker compose logs api` (or the console) for the logged cause. To demo the endpoint without
Yahoo, run `StockQuoteApiIT`, which stubs the `StockQuoteProvider` port and asserts the real
percent-change output.

## Inspecting the database

```bash
docker compose exec postgres psql -U dashboard -d fin_dashboard -c '\dt'
docker compose exec postgres psql -U dashboard -d fin_dashboard \
  -c 'SELECT p.name, s.ticker, s.shares FROM portfolio p LEFT JOIN portfolio_stock s ON s.portfolio_id = p.id;'
```

## When it will not start

- **`Schema validation: missing table [portfolio]`** — Flyway did not run. Check that
  `spring-boot-starter-flyway` is still a dependency; `flyway-core` alone does nothing in Boot 4.
- **Connection refused** — Postgres is not healthy yet. `docker compose ps`.
- **Port 8080 in use** — `SERVER_PORT=8081 ./mvnw spring-boot:run`.
- **Schema drift after editing a migration** — never edit an applied one. Reset the local database:
  `docker compose down -v && docker compose up -d postgres`.

## Resetting

```bash
docker compose down -v    # -v also drops the data volume
```
