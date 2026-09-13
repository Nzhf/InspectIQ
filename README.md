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

## How to Run Locally

### Prerequisites

<!-- TODO: expand in later phases -->
- Java 17+ (JDK)
- Node.js 18+ and npm
- Docker & Docker Compose
- A PostgreSQL instance (or use the Docker Compose setup)
- A Telegram Bot token (for alert-service)

### 1. Clone & Configure

```bash
git clone <repository-url>
cd InspectIQ
cp .env.example .env
# Fill in the real values in .env — see the comments inside .env.example
```

### 2. Start Infrastructure (Database)

<!-- TODO: fill in once docker-compose.yml is configured -->
```bash
docker compose up -d
```

### 3. Run Backend Services

```bash
# ingestion-service (implemented — see ingestion-service/README.md for details)
cd ingestion-service
./gradlew bootRun        # Windows: gradlew.bat bootRun

# analytics-service (implemented — see analytics-service/README.md for details)
cd ../analytics-service
./gradlew bootRun        # Windows: gradlew.bat bootRun

# alert-service (implemented — see alert-service/README.md for details)
cd ../alert-service
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

Once running:
- ingestion-service API: http://localhost:8081 (Swagger UI at http://localhost:8081/swagger-ui.html)
- analytics-service API: http://localhost:8082 (Swagger UI at http://localhost:8082/swagger-ui.html)
- alert-service API: http://localhost:8083 (Swagger UI at http://localhost:8083/swagger-ui.html)
- alert-service endpoints: `GET /api/v1/alerts/status`, `POST /api/v1/alerts/check`
- Health check (any service): http://localhost:8081/actuator/health, http://localhost:8082/actuator/health, http://localhost:8083/actuator/health

### 4. Run the Dashboard

<!-- TODO: fill in once Angular project is initialised -->
```bash
cd dashboard
npm install
npm start
# Open http://localhost:4200
```

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
