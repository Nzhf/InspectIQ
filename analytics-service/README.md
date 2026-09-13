# analytics-service

Read-side query service for the InspectIQ dashboard. Computes aggregated quality
metrics — overall KPIs, yield over time, defect-type distribution and recent
inspections — straight from the shared PostgreSQL schema owned by
`ingestion-service`.

This service is **read-only by design**: it only ever issues `SELECT` statements
and never writes to the database.

## What it does

- Serves the metrics the dashboard charts consume (`/api/v1/metrics/*`)
- Exposes one KPI summary endpoint for the dashboard header (`/api/v1/dashboard/summary`)
- Queries the shared inspection data (same tables as ingestion-service; no Flyway migrations here)
- Uses Postgres aggregate SQL (`FILTER`, `date_trunc`) for the analytics, and JPA
  only for the simple "latest rows" listing

## Running locally

Prerequisites: Java 17+, a running PostgreSQL instance with the InspectIQ schema
(created by ingestion-service's Flyway migrations), and a `.env` file at the repo
root (copy `.env.example`).

```bash
cd analytics-service
gradle bootRun        # Windows note: this repo currently uses a global Gradle install
```

The service starts on port `8082` (configurable via `ANALYTICS_SERVICE_PORT`).

## API endpoints

A live, browsable reference is available at **http://localhost:8082/swagger-ui.html**.
Summary:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/dashboard/summary` | Overall KPIs: units, passes, fails, yield %, batch count |
| GET | `/api/v1/metrics/yield?groupBy=day\|batch&from=&to=` | Yield trend (line chart). `groupBy` defaults to `day` (ISO day buckets); `batch` buckets by batch code |
| GET | `/api/v1/metrics/defects?batchId=&from=&to=` | Defect-type distribution (pie chart) with % share of failed units |
| GET | `/api/v1/inspections/recent?limit=` | Latest inspections (batch code, result, defect code) |

All four are `GET`, take optional `from`/`to` timestamps (ISO-8601), and need no
auth — the dashboard, and later the alert-service, consume them directly. An
unknown `batchId` filter returns a `404` RFC 7807 `application/problem+json`
body; invalid parameters return `400`.

### Example: yield for the last 7 days

```bash
curl "http://localhost:8082/api/v1/metrics/yield?groupBy=day" \
  -H "Content-Type: application/json"
```

## Environment variables

| Variable | Purpose |
|---|---|
| `ANALYTICS_SERVICE_PORT` | HTTP port (default 8082) |
| `ANALYTICS_SERVICE_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/inspectiq` |
| `ANALYTICS_SERVICE_DB_USER` | Database user |
| `ANALYTICS_SERVICE_DB_PASSWORD` | Database password |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API (default `http://localhost:4200`) |

## Testing

```bash
gradle test    # unit tests (no database needed)
```

The service layer is unit-tested with Mockito. The aggregate SQL is deliberately
Postgres-specific and therefore not exercised in unit tests; it is verified
against a real database during manual smoke tests (see the root README for how
to start Postgres via Docker Compose).

## Design notes

- **Why JdbcTemplate for aggregates?** The yield/defect queries use Postgres-only
  features that JPQL cannot express (`COUNT(*) FILTER (WHERE ...)`, `date_trunc`),
  and building the WHERE clause dynamically means absent filters simply drop out
  of the SQL instead of being bound as NULL parameters.
- **Why `@Immutable` JPA entities for the rest?** The "latest rows" listing gets
  eager-fetched joins and pagination for free, while `@Immutable` documents that
  these are read-only views over ingestion-owned tables.
- **No device API key:** read endpoints stay public so the dashboard and the
  alert-service can poll them without credential management.