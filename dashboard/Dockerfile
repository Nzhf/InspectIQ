# ============================================================================
# dashboard — multi-stage: Angular 21 build → nginx SPA host
# ============================================================================
# The production build (default ng build configuration) compiles with
# src/environments/environment.prod.ts, whose API base URLs are RELATIVE
# (/api/ingestion, /api/analytics, /api/alerts) so the SPA and its backends
# share one origin. nginx.conf reverse-proxies those prefixes to the backend
# services by their docker-compose service names.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Deterministic install from the committed lockfile (avoids transitive drift)
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Custom server block: SPA fallback + API reverse proxies + security headers
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/dashboard/browser /usr/share/nginx/html
EXPOSE 80