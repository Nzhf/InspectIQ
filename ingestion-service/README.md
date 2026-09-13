# ingestion-service

Receives raw inspection results from AOI (automated optical inspection) stations, validates them, and persists each record to PostgreSQL. This is the **single entry point for all inspection data** in InspectIQ — no other service writes to the database.

## What it does

- Owns the database schema (Flyway migrations in `src/main/resources/db/migration`)
- Exposes REST endpoints for creating production batches and recording/querying inspection results
- Validates every payload before processing (Bean Validation + a custom cross-field rule mirroring the DB CHECK constraint)
- Protects write endpoints with a shared device API key

## Running locally

Prerequisites: Java 17+, Docker (for PostgreSQL), and a `.env` file at the repo root (copy `.env.example`).

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the service (from the repo root)
cd ingestion-service
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

The service starts on port `8081` (configurable via `INGESTION_SERVICE_PORT`).

## API endpoints

A live, browsable reference is available at **http://localhost:8081/swagger-ui.html** (powered by springdoc-openapi). Summary:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/batches` | API key | Create a production batch |
| GET | `/api/v1/batches/{id}` | — | Fetch a batch with pass/fail summary stats |
| POST | `/api/v1/inspections` | API key | Record a single inspection result |
| GET | `/api/v1/inspections?batchId=&from=&to=&page=&size=` | — | Query inspections with filters |
| GET | `/actuator/health` | — | Health check |

### Example: record an inspection

```bash
curl -X POST http://localhost:8081/api/v1/inspections \
  -H "Content-Type: application/json" \
  -H "X-Device-Api-Key: $DEVICE_API_KEY" \
  -d '{"batchId":"<uuid>","result":"FAIL","defectTypeCode":"SOLDER_BRIDGE","rawData":{"confidence":0.87}}'
```

Errors are returned as RFC 7807 `application/problem+json` with clear messages (400 for validation, 401 for a missing/invalid API key, 404 for unknown batch/defect codes, 409 for duplicate batch codes).

## Environment variables

| Variable | Purpose |
|---|---|
| `INGESTION_SERVICE_PORT` | HTTP port (default 8081) |
| `INGESTION_SERVICE_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/inspectiq` |
| `INGESTION_SERVICE_DB_USER` | Database user |
| `INGESTION_SERVICE_DB_PASSWORD` | Database password |
| `DEVICE_API_KEY` | Shared secret required in the `X-Device-Api-Key` header on POST endpoints. If unset, writes are unprotected (a warning is logged at startup) — set it before exposing the service beyond localhost. |

## Security notes

**Why a shared API key?** The write endpoints simulate a headless AOI station reporting results. A single shared secret is proportionate for a portfolio project: it keeps casual traffic out without the operational cost of a real identity system. A production factory floor would use per-device credentials or mutual TLS so a leaked key can be revoked per station and devices are cryptographically authenticated. The key comparison is constant-time (`MessageDigest.isEqual`) to avoid timing leaks.

**Data integrity:** a PASS inspection must not carry a defect type — enforced both in the API layer (clean 400) and in the database (`chk_defect_only_on_fail` CHECK constraint).

## Testing

```bash
./gradlew test                                        # unit tests (no Docker needed)
./gradlew test -Dtestcontainers.enabled=true          # + integration test (needs Docker)
```

The integration test spins up a real PostgreSQL 16 container via Testcontainers, runs the Flyway migrations, and posts an inspection end-to-end through the HTTP API.

## Mock data generator

`generate-mock-data.ps1` simulates inspection stations posting realistic batches (configurable fail rate, defect mix, station IDs):

```powershell
cd ingestion-service
.\generate-mock-data.ps1 -ApiKey "your-device-key" -Batches 3 -UnitsPerBatch 40
```
