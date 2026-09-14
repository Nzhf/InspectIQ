# InspectIQ Dashboard

Angular 21 dashboard for visualizing AOI inspection quality data. Talks to `analytics-service` (port 8082) and `ingestion-service` (port 8081) via typed HTTP clients.

## Prerequisites

- Node.js 20+ and npm
- The three backend services running (see root README)
- PostgreSQL with the V1 schema applied

## Setup

```bash
cd dashboard
npm install
npm start
```

The dev server runs on `http://localhost:4200` and reloads on file changes.

## Build

```bash
npm run build      # production build to dist/
npm run watch      # development build with watch
```

## Tests

```bash
npm test           # runs unit tests via Vitest (jsdom environment)
```

## How it points at the backend

API base URLs live in `src/environments/environment.ts` (dev) and `src/environments/environment.prod.ts` (prod) — never hardcoded in components:

| Service | Dev URL | Prod URL |
|---|---|---|
| analytics-service | `http://localhost:8082` | `/api/analytics` |
| ingestion-service | `http://localhost:8081` | `/api/ingestion` |
| alert-service | `http://localhost:8083` | `/api/alerts` |

An `enableAlerts` flag in `environment.ts` gates the optional alert-status banner.

## Folder structure

```
src/app/
├── app.component.ts          # root: toolbar nav + router-outlet
├── app.config.ts             # provides HttpClient, Router, Animations
├── app.routes.ts             # / → Overview, /batches, /batches/:id
├── core/
│   ├── models/inspection.ts  # shared TypeScript interfaces (mirror the Java records)
│   └── services/
│       ├── analytics.service.ts   # GET /api/v1/dashboard/summary, /metrics/yield, /metrics/defects
│       ├── ingestion.service.ts   # GET /api/v1/batches, /api/v1/batches/:id, /api/v1/inspections
│       ├── alert.service.ts       # GET /api/v1/alerts/status
│       └── error-handler.service.ts
├── shared/components/
│   ├── loading-spinner.component.ts
│   └── error-alert.component.ts
└── features/
    ├── overview/
    │   ├── overview.component.ts
    │   └── components/
    │       ├── kpi-cards.component.ts
    │       ├── yield-trend-chart.component.ts   # ngx-charts line
    │       ├── defect-pie-chart.component.ts     # ngx-charts pie
    │       └── alert-banner.component.ts
    └── batches/
        ├── batches.component.ts      # Material table + paginator
        └── batch-detail.component.ts # batch info + inspection table
```

## Why these choices

- **Angular Material** for tables, cards, toolbar, paginator — gives a polished, consistent look without hand-rolling CSS for complex components.
- **@swimlane/ngx-charts** for charts — already installed; declarative Angular bindings, no direct DOM manipulation.
- **Typed HttpClient service layer** — components never call `HttpClient` directly; services are injectable and testable with `HttpTestingController`.
- **Feature folder structure** — overview and batches each own their components, keeping the tree scalable as the dashboard grows.

# Dashboard

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.19.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
