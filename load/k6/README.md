# Load testing (k6)

`booking-flow.js` is a [k6](https://k6.io) script that drives RailReserve the way real traffic does:
authenticate, then hammer the **search** and **availability** read paths, with an optional
**hold → confirm** write path.

## Prerequisites
- [k6 installed](https://grafana.com/docs/k6/latest/set-up/install-k6/) (`brew install k6`).
- The stack running with demo data seeded:
  ```bash
  POSTGRES_PORT=5433 docker compose up -d postgres
  cd backend && RAILRESERVE_SEED_ENABLED=true ./gradlew bootRun
  ```

## Run
```bash
# Read-path load (search + availability), 50 VUs for ~1 minute
k6 run load/k6/booking-flow.js

# Turn the knobs
BASE_URL=http://localhost:8080 VUS=100 DURATION=2m k6 run load/k6/booking-flow.js
```

## Exercising the write path (hold → confirm)
Every hold consumes a seat, so the booking flow needs a pool of **distinct, unbooked** seat ids for
one schedule/coach. Query them from the seeded database, then pass them in:
```bash
export SCHEDULE_ID=1 COACH_ID=1
export SEAT_IDS="1001,1002,1003,1004,1005"   # distinct free seats in that coach
k6 run load/k6/booking-flow.js
```
Each VU picks a different seat, so holds don't collide on the same berth.

## Thresholds (the script fails the run if these are breached)
- `http_req_failed` &lt; 1%
- search p95 &lt; 400 ms, availability p95 &lt; 300 ms
- booking errors &lt; 5%
