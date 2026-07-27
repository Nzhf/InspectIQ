# ADR 0002: Relational Database Choice (PostgreSQL vs NoSQL)

## Status

Accepted

## Date

2026-07-26

## Context

We need a primary data store to persist manufacturing quality inspection results, production batch metadata, and defect categories. In previous fast-shipping projects, NoSQL document stores (like Firebase) were chosen for their schema flexibility and rapid setup. For InspectIQ, we must decide if we continue with a NoSQL approach or adopt a relational database.

## Decision

We will use **PostgreSQL** with a normalized relational schema.

The nature of manufacturing inspection data is inherently relational and structured:
1. An inspection result strictly belongs to a specific production batch.
2. A failed inspection references a predefined catalog of defect types.
3. The analytics service will heavily rely on aggregations, time-series querying, and JOINs across these entities (e.g., "yield rate per batch", "top defect types over the last 30 days").

While NoSQL databases offer schema flexibility, our core domain model (Batches -> Inspections -> Defect Types) is stable and structured. Postgres allows us to enforce data integrity at the database level through foreign keys and constraints, ensuring orphaned records or invalid defect codes never enter the system. We will utilize Postgres's `JSONB` column type to retain flexibility for arbitrary `raw_data` (like sensor readings or confidence scores) without sacrificing the relational integrity of the core entities.

## Consequences

- **Positive:** Strong data integrity guarantees (foreign keys, constraints). Excellent support for complex analytical queries (JOINs, aggregations) via standard SQL. Flexible handling of semi-structured data via `JSONB`.
- **Positive:** Fits perfectly with Spring Boot Data JPA / JDBC.
- **Negative:** Requires strict schema management and migrations (via Flyway) compared to schemaless databases, which slightly slows down the initial creation of models.
