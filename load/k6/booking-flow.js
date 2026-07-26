import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// RailReserve load test.
//
// Models the read-heavy path a booking site actually serves at scale: authenticate once, then
// repeatedly search trains and check seat availability. An optional hold -> confirm booking flow is
// enabled when a distinct seat pool is supplied (see load/k6/README.md), because every hold needs a
// seat that is not already taken.
//
// Run against a locally-seeded stack:
//   RAILRESERVE_SEED_ENABLED=true  (start the app so demo data exists)
//   k6 run load/k6/booking-flow.js
//
// Tunables (env vars): BASE_URL, VUS, DURATION, and the booking-flow inputs SCHEDULE_ID / COACH_ID /
// SEAT_IDS (comma-separated) to switch the write path on.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1`;

const STATIONS = ['NDLS', 'BCT', 'CSTM', 'HWH', 'MAS', 'SBC', 'ADI', 'PUNE', 'JP', 'LKO'];

const searchLatency = new Trend('search_latency', true);
const availabilityLatency = new Trend('availability_latency', true);
const bookingErrors = new Rate('booking_errors');

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '15s', target: Number(__ENV.VUS) || 50 },
        { duration: __ENV.DURATION || '1m', target: Number(__ENV.VUS) || 50 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 99% of requests must succeed, and the read path must stay snappy under load.
    http_req_failed: ['rate<0.01'],
    search_latency: ['p(95)<400'],
    availability_latency: ['p(95)<300'],
    booking_errors: ['rate<0.05'],
  },
};

// Register + login once per VU; return the bearer token used for authenticated calls.
export function setup() {
  return { seatIds: (__ENV.SEAT_IDS || '').split(',').filter(Boolean) };
}

function authenticate() {
  const email = `load_${__VU}_${Date.now()}@example.com`;
  const password = 'password123';
  const registerRes = http.post(`${API}/auth/register`, JSON.stringify({
    email, password, fullName: 'Load Test', phone: '9990000000',
  }), { headers: { 'Content-Type': 'application/json' } });
  check(registerRes, { 'registered (201)': (r) => r.status === 201 });

  const loginRes = http.post(`${API}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(loginRes, { 'logged in (200)': (r) => r.status === 200 });
  return loginRes.json('data.accessToken');
}

function pickPair() {
  const from = STATIONS[Math.floor(Math.random() * STATIONS.length)];
  let to = from;
  while (to === from) to = STATIONS[Math.floor(Math.random() * STATIONS.length)];
  return { from, to };
}

function isoDatePlus(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export default function (data) {
  const token = authenticate();
  const authHeaders = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };

  group('search', function () {
    const { from, to } = pickPair();
    const res = http.get(`${API}/trains/search?from=${from}&to=${to}&date=${isoDatePlus(3)}`);
    searchLatency.add(res.timings.duration);
    check(res, { 'search 200': (r) => r.status === 200 });

    const content = res.json('data.content') || [];
    if (content.length > 0 && content[0].scheduleId) {
      group('availability', function () {
        const avail = http.get(`${API}/schedules/${content[0].scheduleId}/availability`);
        availabilityLatency.add(avail.timings.duration);
        check(avail, { 'availability 200': (r) => r.status === 200 });
      });
    }
  });

  // Optional write path: only runs when a seat pool is supplied, since each hold consumes a seat.
  if (data.seatIds.length > 0 && __ENV.SCHEDULE_ID && __ENV.COACH_ID) {
    const seatId = data.seatIds[(__VU - 1) % data.seatIds.length];
    group('hold_confirm', function () {
      const holdBody = JSON.stringify({
        scheduleId: Number(__ENV.SCHEDULE_ID),
        coachId: Number(__ENV.COACH_ID),
        seatIds: [Number(seatId)],
        passengers: [{ name: 'Load Rider', age: 30, gender: 'MALE' }],
      });
      const hold = http.post(`${API}/bookings/hold`, holdBody, authHeaders);
      const ok = check(hold, { 'hold 200': (r) => r.status === 200 });
      bookingErrors.add(!ok);
      if (ok) {
        const holdId = hold.json('data.holdId');
        const confirm = http.post(`${API}/bookings/confirm`,
          JSON.stringify({ holdId, paymentToken: 'tok_load' }), authHeaders);
        bookingErrors.add(!check(confirm, { 'confirm 200': (r) => r.status === 200 }));
      }
    });
  }

  sleep(1);
}
