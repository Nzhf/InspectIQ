# InspectIQ - Security Review

Phase 6 closes the loop on a system whose four components already worked in
isolation. This review audits the **integrated** system against the pillars that
matter for a portfolio-grade service, records what was found, and separates
**fixed** findings from **knowingly accepted** risks.

## Scope & method

- **Scope:** ingestion-service, analytics-service, alert-service, the Angular
  dashboard + its nginx edge, and the `docker-compose.yml` wiring that joins
  them.
- **Method:** manual code reading of every request/response path, a secrets
  scan of the working tree and Git history for the tracked files, and a live
  demonstration of the access-control path against the running stack
  (`POST` without a key -> `401`; see `docs/e2e-test-scenario.md`, step 3).
- **Threat model:** a localhost/dev deployment by default, with an explicit
  note on what changes when it is exposed. This is a demo/portfolio system, not
  a factory-floor control system - the honest limitations are called out rather
  than hidden.

---

## 1. Secrets management

**Finding: OK.** No secret is committed.

- `.env` is **gitignored** (`.gitignore` lists `.env`, `.env.local`,
  `.env.*.local`) and is **untracked** - `git status` never reports it as a
  candidate change. Only `.env.example` is tracked, and it contains
  **placeholders only** (empty `POSTGRES_USER`, `POSTGRES_PASSWORD`,
  `DEVICE_API_KEY`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`).
- `docker-compose.yml` references every credential via `${VAR:-default}`
  interpolation. Real `.env` values always win; the `:-default` fallbacks
  (`inspectiq` / `inspectiq_dev`) exist only so an empty checkout still boots a
  local dev stack. The compose file itself duplicates **no** real secret.
- A scan of tracked sources for token-like literals found only test fixtures:
  `TelegramNotifierTest` uses `123456:ABC-test-token` against a local
  MockWebServer - a mock value that never leaves the JVM.

**Operational note:** the local `.env` holds a real Telegram bot token and a
dev-only API key. Both are gitignored; they must stay that way, and the token
should be rotated if `.env` was ever shared out-of-band.

---

## 2. Input validation

**Finding: OK.** Every write payload is validated before it reaches the domain
layer.

- `POST /api/v1/batches` and `POST /api/v1/inspections` take `@Valid` request
  records with Bean Validation annotations (`@NotNull`, `@Pattern`, `@Size`).
  For example `CreateInspectionRequest` requires a non-null `batchId`, a
  `result` matching `PASS|FAIL` (case-insensitive), and caps `defectTypeCode`
  at 100 characters.
- A **cross-field** rule, `@DefectOnlyOnFail`, rejects a `PASS` inspection that
  also carries a defect code - mirroring the `chk_defect_only_on_fail` database
  CHECK constraint so the API fails cleanly with `400` instead of the DB
  rejecting it later.
- Unknown references are resolved **before** SQL runs: an unknown `batchId` /
  `defectTypeCode` -> `404`, a duplicate `batchCode` -> `409`.
- Query parameters are constrained: `groupBy` is a **whitelist**
  (`day|daily`, `batch|by_batch`, anything else -> `400`), and
  `recentInspections(limit)` is clamped to `[1, MAX_RESULT_LIMIT]` so a caller
  cannot request an unbounded result set.
- All errors are returned as RFC 7807 `application/problem+json`, so failures
  are structured and do not leak stack traces to the client.

---

## 3. Query construction (injection)

**Finding: OK.** No user-controlled string is concatenated into SQL.

- JPA repository queries use **named parameters**.
- `analytics-service` uses `JdbcTemplate` with positional `?` placeholders. The
  dynamic `WHERE` fragment is built by `whereClause(...)`, which only ever emits
  a **fixed set of parameterized predicates** (`ir.batch_id = ?`,
  `ir.inspected_at >= ?`, `ir.inspected_at <= ?`), and `filterArgs(...)` supplies
  the matching bind values in the same order. The clause text contains no user
  data - values travel exclusively as bind parameters.
- `alert-service` measures yield with a single rolling-window aggregate query
  (`YieldMetricsQuery`) using `?` for the lookback limit.

Because the only "dynamic" element is *which* fixed predicates are present (never
their content), there is no injection surface even as filters are added.

---

## 4. CORS

**Finding: OK.** Origins are allowlisted, never wildcarded.

- All three services configure CORS through a `WebConfig` `WebMvcConfigurer`
  whose origins come from `CORS_ALLOWED_ORIGINS` (default
  `http://localhost:4200`, trimmed and split on commas).
- Registrations target `/api/**` explicitly and allow only the methods each
  service actually needs - `GET, POST, OPTIONS` for ingestion,
  `GET, OPTIONS` for the read-only analytics/alert services. There is **no**
  `.allowedOrigins("*")` anywhere.
- No `@CrossOrigin` annotation overrides exist in the codebase, so there is one
  single, auditable place per service where origins are decided.
- In the containerised stack the browser calls the dashboard's **own origin**;
  nginx reverse-proxies `/api/...` server-side, so runtime preflights largely
  disappear - but the CORS config remains correct for the host-run dev workflow
  where Angular (4200) talks to 8081/8082/8083 directly.

---

## 5. Authentication & authorization

**Finding: OK for the stated threat model; limitations documented.**

- Write (`POST`) endpoints on ingestion-service are guarded by
  `DeviceApiKeyFilter`, an `OncePerRequestFilter` that:
  - skips only non-`POST` requests (`shouldNotFilter`), leaving reads open for
    the dashboard and the analytics/alert services by design;
  - compares the supplied `X-Device-Api-Key` using
    **`MessageDigest.isEqual`** - a constant-time comparison, so response timing
    cannot reveal how many leading characters of the key a caller guessed;
  - returns a structured `401 application/problem+json` on a missing/invalid
    key (no detail beyond "missing or invalid").
- **Verified live:** a `POST /api/v1/batches` without the header returns `401`
  against the running container (see `docs/e2e-test-scenario.md`, step 3).
- **Fail-open by design, loudly:** if `DEVICE_API_KEY` is empty the filter logs
  a `WARN` ("POST endpoints are UNPROTECTED...") and allows the request so a
  fresh checkout works without configuration. This is documented below as an
  accepted risk with the operator action required.

---

## 6. Exposed surface (actuator, Swagger)

**Finding: reduced on purpose; Swagger exposure accepted.**

- Actuator exposure is limited to `health,info` on all three services
  (`management.endpoints.web.exposure.include: health,info`). No `env`, `beans`,
  `heapdump`, or `shutdown` endpoints are reachable.
- Each service exposes springdoc-openapi at `/swagger-ui.html` plus the API docs
  JSON. This is **intentionally kept on** for a portfolio project (it is the
  browsable API reference) and is listed as an accepted risk: in a hardened
  deployment these paths would be disabled or gated behind auth.

---

## 7. Transport & container edge

**Finding: reasonable for local; noted for production.**

- `dashboard/nginx.conf` sets defense-in-depth response headers on the SPA
  edge: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and
  `Referrer-Policy: strict-origin-when-cross-origin`.
- Images are multi-stage (the build toolchain is discarded; only the JRE / nginx
  runtime ships) and `npm ci` installs from the committed lockfile for
  reproducible, drift-free builds.
- **Gap (accepted):** the runtime images do **not** declare a non-root `USER`,
  and traffic is plain HTTP locally (TLS terminates at the platform on Render/
  Vercel). Both are appropriate for a local dev/demo stack and are flagged for
  any future hardened deployment.

---

## 8. Gaps found and fixed in Phase 6

These are the concrete issues this phase surfaced once the components were
finally wired together, and the fix applied to each.

| # | Finding | Impact | Fix |
|---|---|---|---|
| 1 | **Alert response contract mismatch** - the dashboard alert banner read fields the alert-service did not return. | Banner would render blank numbers under a real breach | Aligned the DTO/field names end-to-end (`state`, `lastYieldPercent`, `lastSampleUnits`, `yieldThresholdPercent`) and verified the banner renders real values. |
| 2 | **HQL nested-record binding bug** - a query referencing a nested record component produced a parameter-binding error, so ingestion-service failed to start **inside the image** (it happened to pass on a host run). | Service never became healthy in the container | Rewrote the query to bind the nested value correctly; the container now reaches `healthy` and serves traffic. |
| 3 | **npm peer-dependency / Angular build** - the lockfile resolved providers Angular would not accept, breaking the production `ng build` used by the dashboard image. | Dashboard image could not build | Reconciled `package.json`/`package-lock.json` to a mutually compatible set; `npm ci` + `npm run build` now succeed in-image. |
| 4 | **Angular bundle budget exceeded** - the production build failed a size budget once the chart libraries were included. | Dashboard image could not build | Raised the budget to a value that reflects the real (chart-heavy) bundle, keeping the check meaningful. |
| 5 | **`show-sql: true` hard-coded in all three services** - Hibernate echoed every statement, which is noisy and can surface query parameters in container logs. | Log noise + mild data exposure in logs | Made it env-driven and quiet by default: `show-sql: ${HIBERNATE_SHOW_SQL:false}`. Set `HIBERNATE_SHOW_SQL=true` locally to debug queries; documented in `.env.example`. |
| 6 | **Stale doc reference** - `ingestion-service/README.md` documented a `generate-mock-data.ps1` that never existed. | Misleading onboarding | Replaced the section with the real E2E walkthrough (`e2e-smoke.ps1` + `docs/e2e-test-scenario.md`). |

---

## 9. Open risks (documented, not hidden)

| Risk | Why it exists | Mitigation / next step |
|---|---|---|
| **Shared API key** for all stations | Proportionate for a demo; avoids building an identity system | Production would use per-device credentials or mTLS so a leaked key is revocable per station. Comparison is already constant-time. |
| **Fail-open when `DEVICE_API_KEY` is empty** | Lets a fresh checkout run unconfigured | The filter logs a loud `WARN`; operators must set the key before exposing the service. A production build would fail fast instead. |
| **Read endpoints open** (GET) | The dashboard/analytics/alert need them without credentials | Acceptable for read-only aggregates on a demo; a private deployment would gate reads too. |
| **Swagger UI exposed** | Deliberate API documentation for reviewers | Disable or auth-gate it in a hardened deployment. |
| **Images run as root** | Simplest local setup | Add a non-root `USER` in each runtime image before any shared/production deployment. |
| **Bot token travels in the URL path** | The Telegram Bot API requires `.../bot<token>/sendMessage` | A transport-level `RestClientException` message can include the URL (and thus the token); the notifier already logs only a generic failure, so keep it that way or scrub the token before logging. |
| **No rate limiting** | Out of scope for Phase 6 | Add at the edge (nginx `limit_req`) if the service is ever public. |

---

## Summary

The integrated system handles **secrets** (gitignored; placeholders only in the
repo), **validation** (Bean Validation plus a cross-field rule mirrored in the
database), **injection** (parameterized queries exclusively), **CORS** (explicit
allowlist, no wildcard), and **access control** (constant-time API-key check,
verified live with a `401`) correctly for its stated threat model. Phase 6 also
fixed six real integration defects that only appear once the pieces are wired
together. The remaining items are **knowingly accepted** trade-offs for a
portfolio system, each paired with the concrete step a hardened deployment would
take.