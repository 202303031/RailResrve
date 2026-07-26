# RailReserve

A train seat–booking platform built as a portfolio project. The interesting part is
**correctness under concurrency**: many users competing for the same seats, with money
involved, and a hard guarantee that two people never get the same seat — the same way a
bank treats an account balance.

> Full design notes and the reasoning behind every decision live in [LEARNING.md](LEARNING.md).

## Tech stack
- **Backend:** Java 21, Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security + JWT resource server, Bean Validation, Micrometer), Gradle (Kotlin DSL)
- **Database:** PostgreSQL 16 with Flyway migrations
- **Testing:** JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo (80% gate on the booking engine), k6 (load)
- **Frontend:** React 18 + TypeScript, Vite, Tailwind CSS, React Router, axios; Vitest + React Testing Library
- **Infra:** Docker Compose

## Prerequisites
- Docker Desktop (running)
- A JDK to launch Gradle. Java 21 itself is **not** required locally — the Gradle
  toolchain provisions it automatically.

## Quick start (whole stack in Docker)
```bash
cp .env.example .env
docker compose up --build      # Postgres + backend + frontend, with demo data seeded
# Frontend: http://localhost:5173   API: http://localhost:8080/actuator/health
```
`docker compose` starts services in dependency order using healthchecks (DB ready → backend ready →
frontend). Postgres is published on host **5433** by default.

## Local development (services individually)
```bash
cp .env.example .env
docker compose up -d postgres              # just the database
cd backend && ./gradlew bootRun            # API on :8080 (JDK 21 auto-provisioned by Gradle)
cd frontend && npm install && npm run dev  # web on :5173, proxies /api -> :8080
```

## Running the frontend
```bash
cd frontend
npm install
npm run dev     # http://localhost:5173 (proxies /api -> :8080)
```
Seed demo data so search returns trains: run the backend with `RAILRESERVE_SEED_ENABLED=true`.

## Running the tests
```bash
cd backend && ./gradlew check   # full suite + coverage gate (real PostgreSQL via Testcontainers)
cd frontend && npm test          # Vitest + React Testing Library
```
Load test (needs a seeded, running stack and [k6](https://k6.io)): `k6 run load/k6/booking-flow.js`.

## Project layout
```
Rail-Reserve/
├── backend/            # Spring Boot application (search, booking, payments saga, security, metrics)
│   └── Dockerfile      # multi-stage: JDK build -> JRE runtime
├── frontend/           # React + TypeScript SPA
│   ├── Dockerfile      # multi-stage: Node build -> nginx serve (+ /api proxy)
│   └── nginx.conf
├── load/k6/            # k6 load test
├── .github/workflows/  # CI: build -> unit tests -> integration tests -> Docker image build
├── docker-compose.yml  # full stack: Postgres + backend + frontend
├── .env.example        # configuration template
└── LEARNING.md         # design decisions, in plain English
```

## Progress
All 13 phases complete: schema + overbooking guards, search/availability, concurrency-safe
hold/confirm, booking lifecycle, JWT security, payments saga (WireMock-tested), testing + JaCoCo +
k6, observability (Micrometer + correlation ids), the full React frontend, and containerisation +
GitHub Actions CI.
