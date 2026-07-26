# RailReserve — Frontend

A Vite + React + TypeScript + Tailwind single-page app for the RailReserve booking platform.

## Stack
- **React 18** + **TypeScript** (strict)
- **Vite** (dev server + build), **Vitest** + React Testing Library
- **Tailwind CSS** (restrained slate + teal theme)
- **React Router 6**, **axios**

## Getting started
```bash
npm install
npm run dev        # http://localhost:5173 (proxies /api -> http://localhost:8080)
```
The backend must be running (see the repo root README). Seed demo data with
`RAILRESERVE_SEED_ENABLED=true` so search returns trains.

## Scripts
| Script          | Purpose                                    |
|-----------------|--------------------------------------------|
| `npm run dev`   | Vite dev server with API proxy             |
| `npm run build` | Type-check (`tsc --noEmit`) + production bundle |
| `npm test`      | Run the Vitest suite once                  |
| `npm run lint`  | Type-check only                            |

## Architecture
- **`src/api`** — typed API layer. `types.ts` mirrors the backend DTO contract; `client.ts` is the
  axios instance with the JWT request interceptor and the transparent **refresh-on-401**; `endpoints.ts`
  groups the calls; the `ApiResponse` envelope is unwrapped and errors normalised to `ApiRequestError`.
- **`src/auth`** — `AuthContext` (token state synced to `tokenStorage`) and `ProtectedRoute`
  (auth + admin gating).
- **`src/pages`** — the booking journey: search → seat selection → passengers (creates the hold) →
  payment (hold countdown) → confirmation, plus my-bookings, booking detail, and an admin table.
- **`src/components`** — shared UI (buttons, inputs, cards, skeletons, empty states, status badges),
  the toast provider, the error boundary, and the layout.
- **`src/lib`** — formatting helpers and the `useCountdown` hook.

## Configuration
`VITE_API_BASE_URL` (see `.env.example`) — leave empty in development to use the Vite proxy; set to
the API origin in production.
