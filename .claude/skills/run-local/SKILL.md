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
