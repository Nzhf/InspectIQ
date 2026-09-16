# ADR 0003: Inter-Service Communication (Shared Database over Service-to-Service REST)

## Status

Accepted

## Date

2026-09-15

## Context

The original Phase 3 plan assumed `analytics-service` would obtain inspection
data by **calling `ingestion-service`'s REST API** (and, by extension, that
`alert-service` would do the same). The intent was to keep `ingestion-service`
as the sole owner of the database, with every other service going through its
public HTTP surface.

While building Phases 3 and 4 it became clear that the read side has very
different requirements from the write side:

- **Read volume and shape.** Both `analytics-service` and `alert-service` need
  *aggregations* over large sets of rows (yield per day/batch, defect
  distributions, a rolling window of the last N inspections). `ingestion-service`
  exposes record-oriented endpoints (`GET /api/v1/inspections?...`), not
  aggregate ones. Producing a yield figure over the ingestion API would mean
  paging thousands of raw inspection rows over HTTP and aggregating them in the
  caller.
- **Latency and coupling.** The alert monitor runs on a fixed schedule
  (`ALERT_CHECK_INTERVAL_MS`). A rolling-window aggregate it needs is **one SQL
  query**; the same answer over REST is N paginated calls plus in-memory
  arithmetic, and it makes alerting availability a function of
  `ingestion-service` being up *and* fast.
- **Project scope.** This is a single-database portfolio system. A
  database-per-service split (the usual justification for strict API-only
  access) was explicitly deferred in ADR 0002's spirit: one PostgreSQL instance
  is shared by all services, with `ingestion-service` owning the schema and
  Flyway migrations.

## Decision

**Reads bypass the ingestion API: `analytics-service` and `alert-service` query
the shared PostgreSQL database directly.** `ingestion-service` remains the only
service that **writes** to the database and the only owner of the schema
(Flyway migrations live there, and the other services run Hibernate with
`ddl-auto: validate` so they can never mutate it).

Concretely:

- `analytics-service` reads via `JdbcTemplate` with **parameterized** queries
  (see `whereClause(...)` / `filterArgs(...)`), exposing aggregate endpoints the
  dashboard consumes.
- `alert-service` reads with a single rolling-window aggregate query
  (`YieldMetricsQuery`) - it has **no dependency on `analytics-service`**, which
  also means an analytics outage cannot silence alerting.
- `ingestion-service` still exposes its REST API for write clients (AOI
  stations) and for record-level reads (batch tables, batch detail).
- Synchronous REST remains the default for the *write* path and for the
  dashboard -> services surface.

## Consequences

**Positive**

- The rolling-yield alert is one SQL statement instead of a paginated HTTP
  crawl, so it stays cheap and deterministic on a schedule.
- `alert-service` is independent of `analytics-service`: the two can fail
  separately without cascading.
- Aggregations run where the data is (the database), which is what relational
  engines are for; no duplicate aggregation logic in Java.
- Strict schema ownership still holds - only `ingestion-service` migrates, and
  `validate` mode makes that enforceable.

**Negative / trade-offs**

- **Shared-database coupling.** The read services are coupled to the physical
  schema. A column rename in a Flyway migration is a compile-time-and-runtime
  concern for all three services, not a versioned API contract. This is the
  classic shared-database integration risk, accepted here because one team owns
  all three services and the schema.
- **No API contract enforcement.** A REST dependency would have given
  independent deployability and a testable interface between services; direct
  SQL gives neither. As the system grows, an aggregate endpoint on
  `ingestion-service` (or a read-replica/materialised view) becomes the cleaner
  seam.
- **The original Phase 3 assumption is superseded.** Anyone reading the old plan
  should treat this ADR as the accurate record of what was built and why.

**If this decision is revisited** (e.g., splitting the database per service, or
adding a separate reporting store), the trigger would be: independent deploy
cadence for analytics, multi-team ownership of the schema, or read load that
competes with ingestion writes. At that point the migration path is to add
aggregate endpoints (or an event stream) and retire direct SQL in the readers.