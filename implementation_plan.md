# Implementation Plan — Phase 6: Integration, Security Review & Testing Hardening

## Overview

All four InspectIQ components exist and work individually. Phase 6 makes them work **together** and hardens the system as a whole — no new product features.

1. **Dockerize the full stack** — a `docker-compose.yml` that runs Postgres + ingestion-service + analytics-service + alert-service + the Angular dashboard with one command, referencing `.env` for all credentials (no secrets duplicated into the compose file).
2. **E2E test scenario** — a step-by-step manual test script in `docs/e2e-test-scenario.md`: create batch → post inspections incl. failures → verify analytics yield → verify an alert breach → verify the dashboard renders it all.
3. **Security review** — `docs/security-review.md`: secrets (incl. git history), input validation, parameterized queries, CORS scope, API keys actually enforced. **Fix gaps found.**
4. **Health checks** — confirm all `/actuator/health` endpoints; add a "production monitoring" note to `docs/architecture.md`.

## Docker build strategy (key decision)

- No Gradle wrapper exists in any service; the **host global Gradle 9.6.1 is incompatible with the Spring Boot 3.2.5 plugin** (`bootJar`/`bootRun` fail locally — documented in the Phase 4 recap).
- Fix: build each Spring Boot service inside the official **`gradle:8.10-jdk17`** image (`gradle bootJar --no-daemon -x test`), runtime on **`eclipse-temurin:17-jre-alpine`**.
  - WHY `-x test`: unit tests stay a local/CI concern (`gradle test`), and the ingestion integration test uses Testcontainers which needs a Docker socket not mounted into builds.
- Dashboard: **`node:22-alpine`** build (`npm ci` → `npm run build`) → **`nginx:1.27-alpine`** serving `dist/dashboard/browser`, reverse-proxying `/api/ingestion|analytics|alerts/` to the backend service names.
- `docker-compose.yml` credentials use `${VAR:-dev-default}` interpolation — `.env` values always win; defaults make an empty `.env` boot a local dev stack.
- Compose-internal DB host is the compose service name `postgres`, so each service's JDBC URL is composed in the compose file rather than reusing the `.env` `*_DB_URL` (those target `localhost` for host-run workflows).

## Files

### New
| Path | Purpose |
|---|---|
| `ingestion-service/Dockerfile` + `.dockerignore` | Multi-stage build (gradle:8.10 → temurin 17 JRE) |
| `analytics-service/Dockerfile` + `.dockerignore` | Same, port 8082 |
| `alert-service/Dockerfile` + `.dockerignore` | Same, port 8083 |
| `dashboard/Dockerfile` + `.dockerignore` | Angular build → nginx SPA host |
| `dashboard/nginx.conf` | SPA fallback + `/api/...` reverse proxies + security headers |
| `docker-compose.yml` | Full stack: postgres + 3 services + dashboard, healthchecks, `.env`-driven |
| `docs/e2e-test-scenario.md` | Step-by-step manual test script (curl, platform-agnostic) |
| `docs/security-review.md` | Audit findings per pillar + fixes applied |
| `docs/adr/0003-service-communication.md` | ADR for the actual shared-database read model decision (was a committed Phase 3 deliverable, never written) |

### Modified
| Path | Change |
|---|---|
| `ingestion/analytics/alert …/application.yml` | `show-sql` → `${HIBERNATE_SHOW_SQL:false}` (env-driven, off by default) |
| `docs/architecture.md` | "Production Monitoring (next step)" section |
| `README.md` (root) | `docker compose up --build -d` quick-start section |
| `.env.example` | Note compose usage; add `HIBERNATE_SHOW_SQL` |
| `ingestion-service/README.md` | Remove stale reference to `generate-mock-data.ps1` (never existed); point to e2e doc |
| `implementation_plan.md` | This plan |

## Implementation Order

1. Persist this plan; commit pending dashboard polish (4 files) as housekeeping.
2. Confirm Docker daemon (`docker info`).
3. Create the 4 Dockerfiles + `.dockerignore`s + `nginx.conf`.
4. Rewrite `docker-compose.yml`; validate `docker compose config`.
5. Build images one service at a time (`docker compose build <svc>`).
6. `docker compose up -d` → wait healthy → smoke /actuator/health x3 + dashboard 200.
7. Execute the e2e scenario against the live stack; fix anything that breaks.
8. Write `docs/e2e-test-scenario.md` mirroring the verified steps.
9. Security review → `docs/security-review.md` + apply the small gap fixes (show-sql flag, ADR 0003, stale README ref, nginx headers already in step 4).
10. Update `docs/architecture.md` (monitoring note) + root `README.md` + `.env.example`.
11. Re-verify: rebuild, rerun e2e, `gradle test` (unit) x3, dashboard `ng build` in-image.
12. Single commit `feat: dockerize stack + integration & security hardening (Phase 6)` → push
13. `Phase 6 Completed Recap.md` with technical + non-technical summaries.

## Verification Checklist

- `docker compose config` valid; `docker compose up --build -d` healthy (`docker compose ps`).
- `/actuator/health` → `UP` on 8081/8082/8083; dashboard 200 on `:4200`.
- E2E: batch → inspections (incl. fails) → analytics matches → alert `ALERT`→`OK` → dashboard renders.
- `gradle test` green per service (unit, no Docker needed); dashboard `ng build` green in image.
- No secrets in new/changed files (confirmed by review + git scan).

## Open Items / Notes

- `.env` has inline comments (e.g. `ALERT_YIELD_THRESHOLD_PERCENT=95.0   # …`) which Compose may misparse — verify with `docker compose config` and clean `.env` inline comments if needed (not tracked).
- `.env` has empty `POSTGRES_USER`/`POSTGRES_PASSWORD`/`DEVICE_API_KEY` → compose dev defaults apply; e2e still demonstrates the 401 path.
- Docker Desktop must be running before `docker compose up`.