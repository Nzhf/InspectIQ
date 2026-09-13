# Implementation Plan — Phase 5: Angular Dashboard

## Overview

Build an Angular 21 dashboard that visualizes quality data from `analytics-service` and lets the user browse production batches and inspection history from `ingestion-service`. The dashboard uses Angular Material for UI components, `@swimlane/ngx-charts` (already installed) for charts, a typed `HttpClient` service layer, and `environment.ts`/`environment.prod.ts` for API base URLs. A small alert-status banner is included but gated behind a feature flag. A new paginated `GET /api/v1/batches` endpoint is added to `ingestion-service` to back the Batches list page.

## Types

**`dashboard/src/app/core/models/inspection.ts`** (new)
```ts
export interface DashboardSummary {
  totalUnits: number;
  totalPasses: number;
  totalFails: number;
  yieldPercent: number | null;
  totalBatches: number;
}

export interface YieldPoint {
  bucket: string;
  totalUnits: number;
  passCount: number;
  failCount: number;
  yieldPercent: number | null;
}

export interface DefectDistributionItem {
  code: string;
  description: string;
  count: number;
  percentOfFails: number | null;
}

export interface BatchListItem {
  id: string;
  batchCode: string;
  productName: string;
  startedAt: string;
  completedAt: string | null;
  status: string;
  totalInspections: number;
  passCount: number;
  failCount: number;
  passRatePercent: number | null;
}

export interface BatchDetail extends BatchListItem {}

export interface InspectionResponse {
  id: string;
  batchId: string;
  result: string;
  inspectedAt: string;
  defectTypeCode: string | null;
  rawData: Record<string, unknown> | null;
}

export interface AlertStatus {
  state: 'OK' | 'ALERT' | 'INSUFFICIENT_DATA';
  yieldPercent: number | null;
  thresholdPercent: number;
  sampleSize: number;
  lastEvaluatedAt: string | null;
  lastAlertSentAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

## Files

### New files

| Path | Purpose |
|---|---|
| `dashboard/src/environments/environment.ts` | Dev config with API base URLs + feature flag |
| `dashboard/src/environments/environment.prod.ts` | Prod config |
| `dashboard/src/app/core/models/inspection.ts` | All frontend DTO interfaces |
| `dashboard/src/app/core/services/analytics.service.ts` | Typed wrapper over analytics-service |
| `dashboard/src/app/core/services/ingestion.service.ts` | Typed wrapper over ingestion-service |
| `dashboard/src/app/core/services/alert.service.ts` | Typed wrapper over alert-service |
| `dashboard/src/app/core/services/error-handler.service.ts` | Maps HTTP errors to friendly messages |
| `dashboard/src/app/shared/components/loading-spinner.component.ts` | Spinner shown during API calls |
| `dashboard/src/app/shared/components/error-alert.component.ts` | Error banner shown on API failure |
| `dashboard/src/app/features/overview/overview.component.ts` | Overview page: KPI cards + charts + alert banner |
| `dashboard/src/app/features/overview/components/kpi-cards.component.ts` | KPI card components |
| `dashboard/src/app/features/overview/components/yield-trend-chart.component.ts` | ngx-charts yield trend |
| `dashboard/src/app/features/overview/components/defect-pie-chart.component.ts` | ngx-charts defect pie |
| `dashboard/src/app/features/overview/components/alert-banner.component.ts` | Alert status banner (gated) |
| `dashboard/src/app/features/batches/batches.component.ts` | Batches table with pagination |
| `dashboard/src/app/features/batches/batch-detail.component.ts` | Batch detail + inspection table |

### Modified files

| Path | Change |
|---|---|
| `dashboard/package.json` | Add `@angular/material` dependency |
| `dashboard/angular.json` | Add Material prebuilt theme to styles |
| `dashboard/src/app/app.routes.ts` | Add 3 routes |
| `dashboard/src/app/app.config.ts` | Add provideHttpClient, provideAnimations |
| `dashboard/src/app/app.component.ts` | Toolbar + router-outlet |
| `dashboard/src/app/app.component.html` | Toolbar template |
| `dashboard/src/app/app.spec.ts` | Update for new template |
| `dashboard/src/styles.css` | Global styles + Material theme |
| `dashboard/README.md` | Phase 5 documentation |
| `ingestion-service/.../BatchDtos.java` | Add BatchListItem record |
| `ingestion-service/.../ProductionBatchRepository.java` | Add findAllWithCounts query |
| `ingestion-service/.../BatchService.java` | Add listBatches method |
| `ingestion-service/.../BatchController.java` | Add GET /api/v1/batches endpoint |
| `docs/architecture.md` | Add dashboard to architecture |
| `README.md` (root) | Add dashboard run instructions |

## Implementation Order

1. Add `BatchListItem` record to `BatchDtos.java`
2. Add `findAllWithCounts(Pageable)` to `ProductionBatchRepository.java`
3. Add `listBatches(Pageable)` to `BatchService.java`
4. Add `GET /api/v1/batches?page&size` to `BatchController.java`
5. Verify: `gradle compileJava`
6. Add `@angular/material` to `package.json` + `npm install`
7. Add Material theme to `angular.json`
8. Create `environments/environment.ts` and `environment.prod.ts`
9. Create `core/models/inspection.ts`
10. Create `core/services/analytics.service.ts`, `ingestion.service.ts`, `alert.service.ts`
11. Create `core/services/error-handler.service.ts`
12. Update `app.config.ts` with `provideHttpClient()`, `provideAnimations()`
13. Update `app.component.ts` + `app.component.html` with toolbar + nav
14. Update `styles.css`
15. Create `shared/components/loading-spinner.component.ts`
16. Create `shared/components/error-alert.component.ts`
17-21. Create Overview page components
22-23. Create Batches page components
24. Update `app.routes.ts`
25. Update `app.spec.ts`
26. Run `ng build`
27. Run `ng test`
28-30. Documentation
31. Commit and push
