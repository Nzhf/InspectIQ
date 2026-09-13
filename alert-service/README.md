# alert-service

Monitors production yield **directly from the shared PostgreSQL schema** (owned by
`ingestion-service`) and notifies production engineers via the **Telegram Bot API**
when quality drops below a configurable threshold — and again when it recovers.

## What it does

- Periodically measures the yield of the **rolling window**: the most recent
  `ALERT_LOOKBACK_INSPECTIONS` (default 200) inspections, computed in a single
  Postgres aggregate query (`SELECT COUNT(*) FILTER (WHERE result = 'PASS') ...`)
- Compares that yield against `ALERT_YIELD_THRESHOLD_PERCENT` (default 95.0)
- Runs a **state machine** `OK → ALERT → OK` so exactly **one** breach message and
  **one** recovery message are sent per incident — no alert spam while still in ALERT
- Guards against tiny samples: fewer than `ALERT_MIN_INSPECTIONS` (default 20)
  inspections never alerts and is reported as `INSUFFICIENT_DATA`
- If Telegram isn't configured, the service **still evaluates and serves
  `/status`** — it just skips sending (logged once)

## Running locally

Prerequisites: Java 17+, a running PostgreSQL instance with the InspectIQ schema
(created by ingestion-service's Flyway migrations), a `.env` file at the repo root
(copy `.env.example`), and — for real notifications — a Telegram bot token +
chat id.

```bash
cd alert-service
gradle bootRun        # Windows note: this repo currently uses a global Gradle install
```

The service starts on port `8083` (configurable via `ALERT_SERVICE_PORT`).

## API endpoints

A live, browsable reference is available at **http://localhost:8083/swagger-ui.html**.
Summary:

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/alerts/status` | Snapshot: Telegram enabled?, thresholds, current state (`OK` / `ALERT` / `INSUFFICIENT_DATA`), last sample & last alert |
| POST | `/api/v1/alerts/check` | Trigger a manual re-evaluation of the rolling window (same as the scheduled tick), returns the fresh status |

### Example: inspect the monitor

```bash
curl "http://localhost:8083/api/v1/alerts/status" -H "Content-Type: application/json"
```

An unknown route returns a `404` RFC 7807 `application/problem+json` body; invalid
parameters return `400`.

## Environment variables

| Variable | Purpose |
|---|---|
| `ALERT_SERVICE_PORT` | HTTP port (default 8083) |
| `ALERT_SERVICE_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/inspectiq` |
| `ALERT_SERVICE_DB_USER` | Database user |
| `ALERT_SERVICE_DB_PASSWORD` | Database password |
| `ALERT_YIELD_THRESHOLD_PERCENT` | Breach threshold, default `95.0` |
| `ALERT_LOOKBACK_INSPECTIONS` | Rolling window size, default `200` |
| `ALERT_MIN_INSPECTIONS` | Minimum sample before an alert can fire, default `20` |
| `ALERT_CHECK_INTERVAL_MS` | Scheduled tick interval, default `60000` |
| `TELEGRAM_BOT_TOKEN` | Bot token from @BotFather (leave blank to disable sending) |
| `TELEGRAM_CHAT_ID` | Chat/channel to deliver notifications to (leave blank to disable) |
| `TELEGRAM_BOT_API_BASE_URL` | Telegram Bot API base URL (almost always the default `https://api.telegram.org`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API (default `http://localhost:4200`) |

## Testing

```bash
gradle test    # unit tests (no database or Telegram needed)
```

- `YieldAlertMonitorTest` (Mockito): OK→ALERT sends once, no re-send in ALERT,
  recovery sends once, `yield == threshold` stays OK, insufficient sample never
  alerts and is reported as `INSUFFICIENT_DATA`, a disabled notifier still evaluates.
- `AlertMessageBuilderTest`: literal HTML assertions on the rendered messages.
- `TelegramNotifierTest` (**MockWebServer** — offline mock of api.telegram.org):
  correct `/bot<token>/sendMessage` URL + JSON body, `ok:false`→FAILED,
  HTTP 500→FAILED, blank token→DISABLED with zero requests.

## Design notes

- **Why measure from PostgreSQL directly?** The rolling window is a single
  aggregate query over the shared schema (same datasource config as analytics),
  so alerting has no dependency on analytics-service and stays accurate even if
  the analytics API is down.
- **Why a thin `RestClient` call instead of `org.telegram:telegrambots`?** A single
  POST to `/bot{token}/sendMessage` is all the Bot API needs; the thin client is
  simpler and is offline-testable with MockWebServer.
- **Anti-spam by construction:** the state machine only sends on transitions
  (OK→ALERT, ALERT→OK), never on every tick. The `INSUFFICIENT_DATA` state avoids
  alerting on empty or near-empty windows and preserves the internal state across
  data gaps so recovery isn't lost.
- **No JPA entities:** alerting needs one aggregate query and no schema ownership,
  so there are no entities/migrations here (matching the read-only convention).