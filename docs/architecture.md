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
| **dashboard** | `4200` | Angular SPA that renders charts (yield over time, defect breakdown, recent inspections) by consuming analytics-service and ingestion-service REST APIs. |

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

## Future Considerations

- Replace synchronous REST calls with an async event bus for ingestion → analytics flow.
- Add a WebSocket or SSE channel so the dashboard receives live updates without polling.
- Add distributed tracing (e.g., Micrometer + Zipkin) once observability needs grow.

> Design decisions are tracked in [`/docs/adr/`](adr/).
