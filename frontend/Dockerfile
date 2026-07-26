# syntax=docker/dockerfile:1

# --- Stage 1: build the static bundle ------------------------------------------------------------
# `npm ci` from the lockfile gives a reproducible install; `npm run build` type-checks then bundles.
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Stage 2: serve with nginx -------------------------------------------------------------------
# nginx serves the SPA (history-fallback to index.html) and reverse-proxies /api to the backend, so
# the browser talks to a single origin — no CORS in the deployed setup.
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD wget -qO- http://localhost/ > /dev/null 2>&1 || exit 1
