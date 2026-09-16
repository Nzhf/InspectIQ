# InspectIQ

**InspectIQ** is a microservices-based manufacturing quality inspection dashboard, modeled on how a semiconductor/electronics factory would track pass/fail inspection results from an automated optical inspection (AOI) station, aggregate yield and defect trends over time, and alert production engineers on emerging quality issues — all through a clean, real-time web interface.

---

## Architecture at a Glance

| Service | Description |
|---|---|
| **ingestion-service** | Receives raw inspection results from AOI stations via REST API, validates the payload, and persists each record to PostgreSQL. This is the single entry point for all inspection data. |
| **analytics-service** | Queries the inspection data store to compute aggregated metrics — yield rates, defect-type distributions, trend lines — and exposes them via REST endpoints consumed by the dashboard. |
| **alert-service** | Periodically measures production yield directly from the shared PostgreSQL schema, compares it against a configurable threshold (default 95%), and notifies engineers via the Telegram Bot API on breach and recovery — without alert spam. |
| **dashboard** | Angular single-page application that visualises inspection data, yield trends, and defect breakdowns using Chart.js / ngx-charts. Communicates with backend services over REST. |

> For a deeper dive, see [`docs/architecture.md`](docs/architecture.md).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+, Spring Boot 3.x |
| Build Tool | Gradle |
| Database | PostgreSQL (schema versioned with Flyway) |
| Frontend | Angular (latest stable), Chart.js / ngx-charts |
| Inter-service Communication | REST over HTTP, JSON |
| Alerts | Telegram Bot API |
| Local Orchestration | Docker Compose |
| Deployment | Render (backend + Postgres), Vercel (Angular frontend) |

---

## Repository Structure

```
/inspectiq
  /ingestion-service      → Spring Boot service
  /analytics-service      → Spring Boot service
  /alert-service          → Spring Boot service
  /dashboard              → Angular application
  /docs
    /adr                  → Architecture Decision Records
    architecture.md       → System architecture overview
  docker-compose.yml
  .env.example
  README.md               ← you are here
```

---

## Quick Start: the Whole Stack in One Command

Everything — PostgreSQL, the three Spring Boot services and the Angular
dashboard — runs from the repo root with a single command:

```bash
cp .env.example .env          # first run only; fill in real values (see below)
docker compose up --build -d  # build the images and start the whole stack
```

Compose gates startup on health checks, so once the command returns, every
service is up:

| URL | What it is |
|---|---|
| http://localhost:4200 | Dashboard (Angular SPA served by nginx) |
| http://localhost:8081 | ingestion-service (Swagger UI at `/swagger-ui.html`) |
| http://localhost:8082 | analytics-service (Swagger UI at `/swagger-ui.html`) |
| http://localhost:8083 | alert-service (Swagger UI at `/swagger-ui.html`) |
| http://localhost:8081/actuator/health | Health check (same path on 8082 / 8083) |

The dashboard calls the backends through **its own origin** — nginx
reverse-proxies `/api/ingestion/`, `/api/analytics/` and `/api/alerts/` to the
service containers — so no CORS preflight is needed in the containerised stack.

Credentials are read from `.env` via `${VAR:-default}` interpolation: the
compose file never holds a real secret, and an empty `.env` still boots a local
dev stack. Note that an empty `DEVICE_API_KEY` leaves the write endpoints
unprotected (a warning is logged at startup) — set it before exposing the
service beyond localhost.

### Verify the stack end to end

With the stack up, the smoke test drives the full quality pipeline — create a
batch, post 200 inspections (170 PASS / 30 FAIL), verify the analytics
aggregates, then check the alert breach → recovery cycle:

```powershell
.\e2e-smoke.ps1        # Windows PowerShell; run from the repo root
```

Step-by-step curl equivalents and the expected numbers are in
[`docs/e2e-test-scenario.md`](docs/e2e-test-scenario.md). After a code change,
re-run `docker compose up --build -d` to rebuild; `docker compose down -v`
removes the containers **and** the database volume.

---
## How to Run Locally

### Prerequisites

- Docker & Docker Compose (the supported way to run the stack)
- Java 17+ (JDK) and Node.js 18+ with npm — only needed for the host-run
  workflow below, not for `docker compose`
- A Telegram Bot token (for alert-service notifications; optional)

### 1. Clone & Configure

Clone the repository and set up the environment (also covered in [Quick
Start](#quick-start-the-whole-stack-in-one-command) above):

```bash
git clone <repository-url>
cd InspectIQ
cp .env.example .env
# Fill in the real values in .env — see the comments inside .env.example
```

### 2. Start the Stack

The supported path is the containerised stack (covers the database and all
services — see [Quick Start](#quick-start-the-whole-stack-in-one-command)):

```bash
docker compose up --build -d
```

### 3. Run Backend Services on the Host (optional)

Prefer `docker compose` above: the repo pins `gradle:8.10-jdk17` inside the
service images because the repo ships no Gradle wrapper and a host-global
Gradle 9.x cannot run the Spring Boot 3.2.5 Gradle plugin (`bootJar`/`bootRun`
fail). Use the host workflow below only with a Gradle 8.x toolchain installed.

```bash
# ingestion-service (implemented — see ingestion-service/README.md for details)
cd ingestion-service
JAVA_HOME=<path-to-JDK-17> gradle bootRun   # Gradle 8.x toolchain; the repo has no wrapper (`gradlew` will NOT work), and host Gradle 9.x cannot run the Spring Boot 3.2.5 plugin

# analytics-service (implemented — see analytics-service/README.md for details)
cd ../analytics-service
gradle bootRun                      # same Gradle 8.x requirement as above

# alert-service (implemented — see alert-service/README.md for details)
cd ../alert-service
gradle bootRun                      # same Gradle 8.x requirement as above
```

Once running:
- ingestion-service API: http://localhost:8081 (Swagger UI at http://localhost:8081/swagger-ui.html)
- analytics-service API: http://localhost:8082 (Swagger UI at http://localhost:8082/swagger-ui.html)
- alert-service API: http://localhost:8083 (Swagger UI at http://localhost:8083/swagger-ui.html)
- alert-service endpoints: `GET /api/v1/alerts/status`, `POST /api/v1/alerts/check`
- Health check (any service): http://localhost:8081/actuator/health, http://localhost:8082/actuator/health, http://localhost:8083/actuator/health

### 4. Run the Dashboard

```bash
cd dashboard
npm install
npm start
# Open http://localhost:4200
```

The dashboard has three pages:
- **Overview** — current yield rate, today's inspection count, yield trend chart, defect distribution pie chart, and an optional alert-status banner.
- **Batches** — paginated table of production batches with pass/fail summaries.
- **Batch Detail** — drill into a single batch's inspection results.

API base URLs are configured in `dashboard/src/environments/environment.ts` (dev) and `dashboard/src/environments/environment.prod.ts` (prod).

---

## Environment Variables

See [`.env.example`](.env.example) for a full list of required environment variables across all services.

---

## Documentation

- [Architecture Overview](docs/architecture.md)
- [Architecture Decision Records](docs/adr/)

---

## License

This is a portfolio/educational project. No license has been applied yet.
