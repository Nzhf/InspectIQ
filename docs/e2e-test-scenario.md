# InspectIQ - End-to-End Test Scenario

This is the **acceptance walkthrough** for the fully dockerised stack. It drives
the public HTTP surface (ports 4200/8081/8082/8083) end to end and asserts the
numbers that prove the services really work together:

1. dashboard + nginx reverse proxy serve the SPA and route `/api/...` traffic
2. write endpoints reject requests without the device API key (401)
3. a batch is created and 200 inspections are posted (170 PASS / 30 FAIL)
4. analytics reports 85.0% yield with a 3-way defect split
5. the alert service fires on the yield breach (85 < 95)
6. a recovery batch pulls the rolling window back to 100% and the alert clears

Everything below is **platform-agnostic `curl`**; the PowerShell equivalent is
the committed [`e2e-smoke.ps1`](../e2e-smoke.ps1) at the repo root, which runs
the exact same scenario and was used to produce the expected outputs quoted here.

> **Note on JSON bodies:** several examples use `--data @-` with a here-doc so
> the payload is easy to read. On Windows `cmd.exe`, or if your shell mangles
> embedded quotes, see "Portable payloads" at the end for a one-liner form.

---

## 0. Prerequisites

- Docker Desktop running
- A `.env` at the repo root (copy `.env.example`). The stack has dev defaults,
  so an empty `.env` still boots; set `DEVICE_API_KEY` if you want a real key.
  This walkthrough assumes the compose dev-key is `inspectiq-dev-key`.
- `curl` on PATH (ships with Windows 10+, macOS, and every Linux)

---

## 1. Start the stack

```bash
docker compose up --build -d
```

Wait until every container reports `healthy`:

```bash
docker compose ps
```

Expected (order may vary):

```
NAME                  STATUS
inspectiq-postgres    Up (healthy)
inspectiq-ingestion   Up (healthy)
inspectiq-analytics   Up (healthy)
inspectiq-alert       Up (healthy)
inspectiq-dashboard   Up (healthy)
```

`ingestion-service` must be healthy before `analytics`/`alert`/`dashboard`
start, because it owns the Flyway migrations that create the schema.

---

## 2. Smoke the health endpoints and the dashboard

Each Spring Boot service exposes only `health` and `info` on the actuator.

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
```

Expected for all three:

```json
{"status":"UP"}
```

The dashboard is an Angular SPA served by nginx, and nginx reverse-proxies
`/api/ingestion/`, `/api/analytics/` and `/api/alerts/` to the services:

```bash
# SPA shell -> 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:4200/

# reverse proxy -> same JSON you get from the service ports directly
curl -s http://localhost:4200/api/ingestion/api/v1/batches?page=0\&size=1
curl -s http://localhost:4200/api/analytics/api/v1/dashboard/summary
curl -s http://localhost:4200/api/alerts/api/v1/alerts/status
```

Expected: `200`, then a paged batch list, the dashboard summary, and
`{"state":"INSUFFICIENT_DATA", ...}` (fresh database, no inspections yet).

---

## 3. Prove the API key is enforced

`POST` endpoints require the shared secret in the `X-Device-Api-Key` header.
Sending a write **without** it must be rejected with `401`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST http://localhost:8081/api/v1/batches \
  -H "Content-Type: application/json" \
  -d '{"batchCode":"NO-AUTH-BATCH","productName":"x","status":"IN_PROGRESS"}'
```

Expected:

```
401
```

The same request **with** the header succeeds - that is the next step.

---

## 4. Create a production batch

```bash
curl -s -X POST http://localhost:8081/api/v1/batches \
  -H "Content-Type: application/json" \
  -H "X-Device-Api-Key: inspectiq-dev-key" \
  --data @- <<'JSON'
{
  "batchCode": "E2E-BATCH-001",
  "productName": "PCB-A-mainboard",
  "status": "IN_PROGRESS"
}
JSON
```

Expected (id is a fresh UUID):

```json
{"id":"c25cd2c1-3286-43c5-ac1c-fe380c565810","batchCode":"E2E-BATCH-001","productName":"PCB-A-mainboard","status":"IN_PROGRESS","startedAt":"..."}
```

Capture the id for the next step:

```bash
BATCH1=$(curl -s -X POST http://localhost:8081/api/v1/batches \
  -H "Content-Type: application/json" \
  -H "X-Device-Api-Key: inspectiq-dev-key" \
  -d '{"batchCode":"E2E-BATCH-CAP","productName":"PCB-A-mainboard","status":"IN_PROGRESS"}' \
  | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "$BATCH1"
```

> Use a **unique** `batchCode` per run - duplicate codes are rejected with `409`
> (by design). The committed `e2e-smoke.ps1` appends a timestamp for this reason.

---

## 5. Post 200 inspections (170 PASS / 30 FAIL)

Post 170 passing units, then 30 failures spread evenly across three defect
codes so the defect distribution has a clean 10 / 10 / 10 split.

```bash
for i in $(seq 1 170); do
  curl -s -o /dev/null -X POST http://localhost:8081/api/v1/inspections \
    -H "Content-Type: application/json" \
    -H "X-Device-Api-Key: inspectiq-dev-key" \
    -d "{\"batchId\":\"$BATCH1\",\"result\":\"PASS\"}"
done

for i in $(seq 1 30); do
  case $((i % 3)) in
    0) DEFECT=SOLDER_BRIDGE ;;
    1) DEFECT=MISALIGNMENT  ;;
    2) DEFECT=SCRATCH       ;;
  esac
  curl -s -o /dev/null -X POST http://localhost:8081/api/v1/inspections \
    -H "Content-Type: application/json" \
    -H "X-Device-Api-Key: inspectiq-dev-key" \
    -d "{\"batchId\":\"$BATCH1\",\"result\":\"FAIL\",\"defectTypeCode\":\"$DEFECT\"}"
done
```

Notes:

- A `PASS` inspection must **not** carry a defect type - that is rejected with
  `400` (and enforced again by a database CHECK constraint).
- An unknown `batchId` or `defectTypeCode` is rejected with `404`.
- A failure payload looks like:
  `{"batchId":"<uuid>","result":"FAIL","defectTypeCode":"SOLDER_BRIDGE"}`

---

## 6. Verify analytics

Summary - yield is passes / (passes + fails) = 170 / 200 = **85.0%**:

```bash
curl -s http://localhost:8082/api/v1/dashboard/summary
```

Expected:

```json
{"totalUnits":200,"totalPasses":170,"totalFails":30,"yieldPercent":85.0,"totalBatches":1}
```

Defect distribution - three codes, 10 each, 33.33% of all failures:

```bash
curl -s http://localhost:8082/api/v1/metrics/defects
```

```json
[
  {"code":"MISALIGNMENT","count":10,"percentOfFails":33.33},
  {"code":"SCRATCH","count":10,"percentOfFails":33.33},
  {"code":"SOLDER_BRIDGE","count":10,"percentOfFails":33.33}
]
```

Yield trend grouped by day (`groupBy` is a whitelisted enum - anything else is
`400`):

```bash
curl -s 'http://localhost:8082/api/v1/metrics/yield?groupBy=day'
```

```json
[{"bucket":"2026-09-15","totalUnits":200,"passCount":170,"failCount":30,"yieldPercent":85.0}]
```

---

## 7. Verify the ingestion batch list

```bash
curl -s 'http://localhost:8081/api/v1/batches?page=0&size=10'
```

Expected: the E2E batch with `totalInspections=200`, `passCount=170`,
`failCount=30`, `passRatePercent=85.0`.

---

## 8. Verify the alert fires

The alert service measures yield over the **most recent 200 inspections** and
breaches when it drops below 95%. With 200 units at 85%, it must fire:

```bash
curl -s -X POST http://localhost:8083/api/v1/alerts/check \
  -H "Content-Type: application/json"
```

Expected:

```json
{"state":"ALERT","lastYieldPercent":85.0,"lastSampleUnits":200,"yieldThresholdPercent":95.0}
```

`GET /api/v1/alerts/status` returns the same snapshot without re-evaluating.

---

## 9. Verify recovery clears the alert

Create a second batch and post 200 passing units. The rolling window now holds
only these 200 PASS records, so yield is 100% and the alert clears:

```bash
BATCH2=$(curl -s -X POST http://localhost:8081/api/v1/batches \
  -H "Content-Type: application/json" \
  -H "X-Device-Api-Key: inspectiq-dev-key" \
  -d '{"batchCode":"E2E-RECOVERY-CAP","productName":"PCB-B","status":"COMPLETED"}' \
  | sed -E 's/.*"id":"([^"]+)".*/\1/')

for i in $(seq 1 200); do
  curl -s -o /dev/null -X POST http://localhost:8081/api/v1/inspections \
    -H "Content-Type: application/json" \
    -H "X-Device-Api-Key: inspectiq-dev-key" \
    -d "{\"batchId\":\"$BATCH2\",\"result\":\"PASS\"}"
done

curl -s -X POST http://localhost:8083/api/v1/alerts/check \
  -H "Content-Type: application/json"
```

Expected:

```json
{"state":"OK","lastYieldPercent":100.0,"lastSampleUnits":200,"yieldThresholdPercent":95.0}
```

This is the anti-spam behaviour: exactly one breach notification on the way
down, one recovery notification on the way up - no repeats while the state is
unchanged.

---

## 10. Verify the dashboard renders it

Open <http://localhost:4200> and confirm:

- **Overview** - yield tile, yield trend chart with the day bucket, defect pie
  chart with the 3-way split, and the alert banner reflecting the current state.
- **Batches** - both E2E batches listed with their pass/fail summaries.
- **Batch detail** - drill into a batch to see individual inspection rows.

All reads go through the nginx proxy, so a rendering dashboard also proves the
`/api/...` routing.

---

## Teardown

```bash
docker compose down          # stop, keep data
docker compose down -v       # stop and delete the Postgres volume (fresh start)
```

---

## Expected results summary

| Step | Assertion | Expected |
|---|---|---|
| 2 | `/actuator/health` x3 | `{"status":"UP"}` |
| 2 | dashboard `/` | `200` |
| 3 | POST without API key | `401` |
| 4 | POST batch with key | returns batch UUID |
| 5 | POST 200 inspections | no errors |
| 6 | `dashboard/summary` | `yieldPercent=85.0`, `totalFails=30` |
| 6 | `metrics/defects` | 3 codes x 10, `33.33` each |
| 6 | `metrics/yield?groupBy=day` | one bucket, `yieldPercent=85.0` |
| 7 | `batches` | `passRatePercent=85.0` |
| 8 | `alerts/check` | `state=ALERT`, `lastYieldPercent=85.0` |
| 9 | `alerts/check` after recovery | `state=OK`, `lastYieldPercent=100.0` |

---

## Portable payloads

If here-docs or quoted JSON are awkward in your shell, the payloads are small
enough to inline. The only rule is that the JSON is valid, e.g.:

```bash
curl -s -X POST http://localhost:8081/api/v1/inspections \
  -H "Content-Type: application/json" \
  -H "X-Device-Api-Key: inspectiq-dev-key" \
  -d '{"batchId":"'"$BATCH1"'","result":"PASS"}'
```

On PowerShell, prefer `ConvertTo-Json` (no embedded double quotes to escape) -
exactly what [`e2e-smoke.ps1`](../e2e-smoke.ps1) does:

```powershell
$body = @{ batchId = $b1.id; result = 'PASS' } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri ($ing + '/api/v1/inspections') -Method Post -Headers $headers `
  -ContentType 'application/json' -Body $body
```
