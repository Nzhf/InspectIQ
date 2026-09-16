# InspectIQ — System Architecture

## Overview

InspectIQ follows a **service-oriented architecture** composed of three independently deployable Spring Boot backend services and one Angular frontend. Each service owns a single, well-defined responsibility within the inspection quality pipeline:

1. **Data ingestion** — accepting and persisting raw inspection results.
2. **Analytics** — computing and serving aggregated quality metrics.
3. **Alerting** — monitoring metrics and notifying engineers when thresholds are breached.

All inter-service communication uses **synchronous REST/HTTP with JSON payloads**. There is no message broker in the current design; this keeps the local development loop simple and the deployment footprint small. If throughput or decoupling demands grow, introducing an event bus (e.g., RabbitMQ or Kafka) is a natural next step — that decision would be captured in an ADR.

The **dashboard** is a standalone Angular SPA deployed separately and communicates exclusively with the backend services via their public REST APIs.

---

## Service Responsibilities & Ports

| Service | Port | Responsibility |
|---|---|---|
| **ingestion-service** | `8081` | Accepts inspection result payloads via `POST /api/v1/inspections`, validates input, and writes records to PostgreSQL. Serves as the sole data-entry point. |
| **analytics-service** | `8082` | Reads inspection data from PostgreSQL, computes aggregations (yield %, defect distribution, trends), and exposes them via `GET` endpoints for the dashboard. |
| **alert-service** | `8083` | Periodically measures production yield directly from the shared PostgreSQL schema (one rolling-window aggregate query — no analytics-service dependency), compares it against a configurable threshold (default 95%), and sends Telegram notifications on breach and recovery. |
| **dashboard** | `4200` | Angular SPA that renders charts (yield over time, defect breakdown) and batch/inspection tables by consuming analytics-service and ingestion-service REST APIs. Uses Angular Material for UI components and @swimlane/ngx-charts for visualizations. |

---

## Data Flow

```
AOI Station / Test Client
        │
        ▼
┌──────────────────┐      writes      ┌──────────────┐
│ ingestion-service │ ───────────────► │  PostgreSQL   │
└──────────────────┘                  └──────┬───────┘
                                               │ reads
                    ┌──────────────────────────┼──────────────────────────┐
                    ▼                          ▼                          ▼
          ┌──────────────────┐       ┌──────────────────┐
          │ analytics-service │       │   alert-service  │
          └─────────┬────────┘       └─────────┬────────┘
                    │ exposes metrics          │ Telegram Bot API
                    ▼                          ▼
          ┌─────────────┐            ┌──────────────────┐
          │  dashboard  │            │ engineers (chat) │
          │  (Angular)  │            └──────────────────┘
          └─────────────┘
```

---

## Database

- **Engine:** PostgreSQL
- **Schema management:** Flyway (versioned SQL migrations managed by the `ingestion-service`).
- **Shared vs. separate databases:** For simplicity in this portfolio project, all services share a single PostgreSQL database. A production system would likely use a database-per-service pattern.

### Data Model (ER Description)
- **`production_batches`**: Represents a production run. Contains `id` (UUID, PK), `batch_code` (Unique), `product_name`, `started_at`, `completed_at`, and `status`.
- **`defect_types`**: A lookup table for standard defect categories. Contains `id` (SERIAL, PK), `code` (Unique), and `description`.
- **`inspection_results`**: Individual unit inspection outcomes. Contains `id` (UUID, PK), `batch_id` (FK to `production_batches`), `result` ('PASS'/'FAIL'), `inspected_at`, `defect_type_id` (FK to `defect_types`, Nullable), and `raw_data` (JSONB).

**Relationships & Integrity:**
- An inspection result must belong to a batch (`batch_id` is NOT NULL).
- If an inspection result is 'PASS', the `defect_type_id` must be NULL (enforced via CHECK constraint).
- Deleting a batch or defect type is restricted if it is referenced by any inspection result (enforced via ON DELETE RESTRICT).

---

## Deployment Topology

| Component | Target Platform |
|---|---|
| ingestion-service | Render (Web Service) |
| analytics-service | Render (Web Service) |
| alert-service | Render (Web Service or Background Worker) |
| PostgreSQL | Render (Managed PostgreSQL) |
| dashboard | Vercel (Static / SSR) |

---

## Security Notes (Portfolio Scope)

- **Authentication:** Not implemented in Phase 0. Will be added in a later phase — likely a lightweight API key or JWT mechanism scoped appropriately for a demo/portfolio project. A production system would integrate with an identity provider (e.g., Keycloak, Auth0).
- **Secrets:** All credentials are stored in environment variables (`.env`), never in source code.
- **Input validation:** Every REST endpoint validates incoming payloads before processing.

---

## Production Monitoring (next step)

Phase 6 ships the stack with **liveness only**: each service exposes
`/actuator/health`, and `docker compose` uses it for container healthchecks. That
tells you a process is up, but nothing about *how* it is behaving. The next step
answers "is the system healthy in the product sense?" — the pieces below are the
pragmatic path, in order of value.

### 1. Metrics — Micrometer + Prometheus + Grafana

- The services are Spring Boot, so **Micrometer** is already on the classpath
  through Actuator. Expose it by adding `prometheus` to the actuator exposure
  list plus the `micrometer-registry-prometheus` dependency; each service then
  serves a scrape endpoint at `/actuator/prometheus`.
- Beyond automatic JVM/HTTP metrics (`http_server_requests_seconds`, GC,
  connection-pool saturation), add **domain gauges** that matter here:
  inspections ingested per minute, rolling yield percent, and *seconds since the
  last inspection received* — a silent station is a real production failure and
  it is invisible to a liveness probe.
- **Prometheus** scrapes the three endpoints; **Grafana** holds one dashboard
  per concern: request latency/error rate per service, ingestion throughput, and
  the yield trend drawn against the alert threshold.
- Alerting then moves from "the process is down" to symptom-based rules
  (error-rate burn, ingestion stalled, yield below threshold) via Alertmanager —
  complementing, not replacing, the in-app yield monitor built in Phase 4.

### 2. Logs — structured JSON → Loki (or ELK)

- Today logs go to stdout via Logback. That is fine for `docker compose logs`
  but not queryable across containers. Switch the console encoder to **JSON**
  (e.g. `logstash-logback-encoder`) so every line carries timestamp, level,
  logger, message and MDC fields instead of free text.
- The highest-value addition is **correlation**: put a request id in the MDC at
  the edge (nginx or a servlet filter) and stamp it on every log line, so one
  browser action can be traced across ingestion → analytics → alert.
- **Loki + Promtail** (or Filebeat → Elasticsearch when full-text search is
  needed) indexes the stream, and Grafana renders those logs next to the metrics
  from step 1 on the same timeline — which is what makes incident triage fast.
- With JSON logs, `HIBERNATE_SHOW_SQL` should stay `false` in shipped
  containers (already the default there — see `.env.example`) so SQL noise does
  not drown the signal.

### 3. Synthetic uptime checks

- Metrics and logs are *passive*: they describe what happened, not what a user
  experiences right now. Add a small synthetic probe that walks the real user
  path on a schedule:
  1. `GET http://<host>:4200/` → SPA shell `200`
  2. `GET /api/analytics/api/v1/dashboard/summary` through the nginx proxy
  3. `GET /actuator/health` on 8081 / 8082 / 8083
- Run it from **outside** the compose network (a hosted checker or a cron
  container) so it catches edge failures — TLS expiry, DNS, nginx — that an
  in-cluster probe would miss. [`e2e-smoke.ps1`](../e2e-smoke.ps1) and
  [`docs/e2e-test-scenario.md`](e2e-test-scenario.md) are already the functional
  core of that probe; the production version is the same steps on a timer with
  alerting on failure.
- A cheap third layer is **uptime monitoring with content assertions** (expect
  the summary JSON to contain `totalUnits`), which distinguishes "the endpoint
  answers" from "the endpoint answers *correctly*".

---

## Future Considerations

- Replace synchronous REST calls with an async event bus for ingestion → analytics flow.
- Add a WebSocket or SSE channel so the dashboard receives live updates without polling.
- Add distributed tracing (e.g., Micrometer + Zipkin) once observability needs grow.

> Design decisions are tracked in [`/docs/adr/`](adr/).
