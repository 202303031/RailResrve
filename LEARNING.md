# LEARNING.md

This file is my study guide. After every phase I write down, in plain English:

1. **What** I built.
2. **Why** I built it that way.
3. What **alternative** I considered and rejected, and why.

I revise from this file before interviews, so it avoids jargon where possible and
explains the reasoning an interviewer is likely to probe.

---

## Phase 1 — Project Scaffold

### What I built
- A single Git repository (a "monorepo") with three parts:
  - `backend/`  — the Spring Boot application (the real project).
  - `frontend/` — the React app (added later, in Phase 10).
  - Root-level infrastructure: `docker-compose.yml`, `.env.example`, this file, and the README.
- A Spring Boot backend that starts up, connects to a PostgreSQL database, runs a
  database migration with Flyway, and exposes a health-check URL at `/actuator/health`.
- A PostgreSQL 16 database that runs in Docker, so nobody has to install a database by hand.
- A test that boots the whole application against a **real** PostgreSQL database (started
  automatically by Testcontainers) and checks it comes up cleanly.

### Key decisions and why

**1. Spring Boot 4.1 instead of the 3.x in the original brief.**
When I went to create the project, the official Spring "Initializr" tool refused to
generate anything below version 4.0 — the 3.x line has passed its free support window.
Rather than start a brand-new project on an unsupported version, I moved up to the
current supported release, Spring Boot 4.1. The concepts are identical; a few names
changed (for example the web starter is now `spring-boot-starter-webmvc`, and it pairs
with Spring Security 7 instead of 6). Interview line: *"the framework generation was
chosen to be currently-supported; the concurrency and design work is version-independent."*

**2. Gradle with the Kotlin DSL.**
Gradle is the build tool. I used the Kotlin flavour of the build file
(`build.gradle.kts`) because it gives type-safety and IDE auto-complete, which catches
build-script mistakes at edit time instead of at build time.

**3. Java 21, pinned with a "toolchain".**
The build declares that the project must be compiled and run with Java 21, using a
Gradle feature called a *toolchain*. Everyone therefore builds with the exact same Java
version regardless of what is installed on their laptop — this machine actually has
Java 22 installed, but the build still uses 21. If Java 21 is missing, Gradle downloads
it automatically (the Foojay resolver plugin). This kills the classic "works on my
machine" problem.

**4. Flyway owns the database schema; Hibernate only validates it.**
Two tools *can* create tables: Flyway (hand-written SQL files that run in order) and
Hibernate (can auto-generate tables from Java classes). I set Hibernate to
`ddl-auto: validate`, which means Hibernate is only allowed to *check* that the tables
match my Java classes — it may never change the database. All schema changes go through
version-controlled Flyway files.
- Why it matters: in a bank you must know exactly what will run against production.
  Hand-written migrations are predictable and auditable.
- Rejected alternative: `ddl-auto: update`. Convenient in a tutorial, dangerous in
  production because it is non-deterministic and can silently drop or alter columns.

**5. Real PostgreSQL everywhere, including tests (Testcontainers), never H2.**
A common shortcut is to run tests against an in-memory H2 database. I deliberately did
not. Tests use Testcontainers, which starts a genuine PostgreSQL 16 in Docker for the
test and throws it away afterwards.
- Why: H2 and PostgreSQL behave differently, especially around row locking and
  transactions — which is the entire hard part of this project (Phase 4). Testing on H2
  would prove nothing about production behaviour. This is the single most important
  foundational choice for a concurrency project.

**6. Configuration from environment variables (the "12-factor" style).**
Database URL, username and password are read from environment variables with sensible
local defaults. The same build runs on my laptop, in Docker, and in CI without editing
code. `.env.example` documents every variable; the real `.env` is git-ignored.

**7. `open-in-view` turned off.**
This Spring setting (on by default) keeps a database connection open during web-page
rendering. I turned it off because under load it can quietly exhaust the connection
pool. Turning it off also forces all data loading into the service layer, which is cleaner.

### How I proved it works
- `./gradlew test` boots the full app against a real PostgreSQL (Testcontainers),
  applies the Flyway baseline migration, and passes.
- Running the stack for real (`docker compose up -d postgres`, then the app) and calling
  `GET /actuator/health` returns HTTP 200 with `{"status":"UP"}`.

### What's next
Phase 2: design the real database tables (stations, trains, schedules, coaches, seats,
inventory, bookings, …) as Flyway migrations, and map them to Java entity classes with
correct relationships and `equals`/`hashCode`.

---

## Phase 2 — Domain & Schema

### What I built
- 12 database tables via three Flyway migrations (catalog, scheduling, booking).
- 12 JPA entity classes mapped to those tables, all sharing a base class for identity.
- **Two database-level guards that make overbooking structurally impossible.**
- A seeding component that fills the database with demo data on demand.

### Key decisions and why

**1. The database is the last line of defence against overbooking — two guards.**
The whole project is about never selling the same seat twice. I put two guarantees in the
database itself, so even a bug in my Java code cannot oversell:
- *Aggregate guard:* `seat_inventory.available_count` has a `CHECK (available_count >= 0)`.
  The "seats left" counter can never go below zero.
- *Per-seat guard:* a **partial unique index** on `seat_hold(seat_id) WHERE status IN
  ('ACTIVE','CONFIRMED')`. A seat can have at most one *live* claim at a time. If two people
  try to grab the same seat at the same instant, both send an INSERT; the database lets
  exactly one through and rejects the other.

I proved both with tests that try to violate them and expect a database error. Why two? The
counter answers "how many seats left?" cheaply; the per-seat index answers "is THIS seat
taken?" exactly. Belt and braces.

**2. Flyway owns the schema; entities must match it (checked automatically).**
Every table is hand-written SQL. Hibernate is set to `validate`, so on startup it checks
that every entity maps to a real column — if I mistype a mapping, the app refuses to start,
and my smoke test catches it. Rejected: letting Hibernate generate the schema (unpredictable
and unsafe for real data).

**3. Surrogate keys + one shared identity rule.**
Every table has a `BIGINT` identity primary key. All entities extend `AbstractEntity`, which
implements `equals`/`hashCode` once, safely: `hashCode` is constant per type (stable even
before the row is saved and still has a `null` id), and `equals` compares the id and unwraps
Hibernate proxies. This avoids the classic bug where an entity "vanishes" from a `HashSet`
after being saved. A natural business key (like PNR) would also work; I chose one consistent
rule for every entity.

**4. Enums stored as text, with a special case for travel classes.**
Statuses/roles/types are stored as their names (`CONFIRMED`, `ADMIN`, …) and guarded by
`CHECK` constraints, so an invalid value cannot get into the database. Travel classes are the
exception: codes like `3A`/`2A` are not legal Java identifiers, so `TravelClass` carries a
`code` and converts at every edge — database (`AttributeConverter`), JSON, and URL binding.
A tidy example of decoupling a domain type from its external representation.

**5. Money is `BigDecimal`; time is UTC.**
Fares use `NUMERIC(10,2)` / `BigDecimal` — never floating point, so you never lose a cent.
Timestamps are stored as `timestamptz` and handled as `Instant` in UTC, so holds expire
correctly regardless of the server's timezone.

**6. Seed data is separate from migrations, and off during tests.**
Demo data (30 stations, 10 trains, 60 days of schedules, ~110k seats) is generated by a
`DataSeeder` component, not a migration, because (a) migrations should be pure schema and
(b) the integration tests run migrations on a fresh database and must not be slowed by
110k rows. The seeder is idempotent, generates dates relative to "today" (so the demo always
has future trains), and only runs when `railreserve.seed.enabled=true`. Tests build their own
tiny fixtures instead.

### The two-phase claim model (this sets up Phase 4)
A seat is claimed in two steps: a temporary **HOLD** (with a TTL) and then a **CONFIRMED**
booking after payment. The `seat_hold` row lives for the whole life of the booking (`ACTIVE`
while held, `CONFIRMED` once paid), which is exactly what lets a single partial unique index
protect the seat from the moment it is held all the way through confirmation.

### How I proved it works
- Hibernate `validate` passes: all 12 entities match the schema.
- A test inserts two live holds on one seat → the database rejects the second.
- A test forces `available_count` negative → the database rejects it.
- A test saves a booking with a passenger, clears the cache, reloads, and finds it intact.
- The seeder builds the full dataset and the ADI→BCT search returns the expected train.

### What's next
Phase 3: the search and availability endpoints, DTOs (Java records) kept strictly separate
from entities, Bean Validation on inputs, global exception handling, and pagination.

---

## Phase 3 — Search & Availability

### What I built
- Two read endpoints: **search** trains between two stations on a date, and check **seat
  availability** for a schedule.
- A single response envelope (`ApiResponse`) used by every endpoint.
- A custom exception hierarchy plus one global handler that turns any error into a clean,
  consistent JSON shape.
- Input validation and pagination.
- DTOs (Java records) that are completely separate from the database entities.

### Key decisions and why

**1. Entities never leave the controller — DTOs do.**
Database entity classes are used only inside the service/repository layer. Everything that
crosses the HTTP boundary is a plain record (`TrainSearchResult`, `AvailabilityResponse`, …).
Why: (a) security — I never accidentally expose internal fields or lazy relationships;
(b) stability — I can change the database without breaking the API; (c) no accidental
lazy-loading errors during JSON serialization.

**2. One response shape for everything (`ApiResponse`).**
Every response is `{ success, data, error }` with exactly one of `data`/`error` filled. The
frontend can then handle every call the same way.

**3. One place handles every error (`@RestControllerAdvice`).**
A single `GlobalExceptionHandler` converts exceptions to responses. My own errors form a
small hierarchy (`ApiException` → `ResourceNotFoundException` / `BusinessRuleException` /
`ConflictException`), each carrying an `ErrorCode` that knows its HTTP status. Validation
failures, bad parameters, unknown URLs, and truly unexpected errors are each handled
explicitly; unexpected ones return a generic 500 without leaking stack traces. Controllers
stay clean — they never write try/catch.

**4. The interesting query: "which trains run from A to B on this date?"**
A train serves A→B only if, on its route, the stop at A comes *before* the stop at B. I
express this by joining the route-stops table to itself — once for the origin, once for the
destination — and requiring `origin.stop_order < destination.stop_order`. Then I run ONE more
query to fetch seat counts grouped by class for all matching schedules, so the whole search
is two queries, not one-plus-N.

**5. Validation at the edge.**
Query parameters are validated with Bean Validation (origin/destination must not be blank,
date is required and must be a real date). A same-origin-and-destination search is caught as
a business rule. All of these produce a 400 with a clear message and, for field errors, which
field failed.

**6. Pagination that doesn't leak the framework.**
Search is paged with Spring Data's `Pageable`, but results are wrapped in my own
`PageResponse` record rather than Spring's `Page` object — because Spring's `Page` JSON shape
is not a stable contract and can change between versions.

**7. A read model, not entities, from the database.**
The availability query builds `CoachAvailability` records directly in the query (a JPQL
"constructor expression"), so the database returns exactly the shape the API needs without
loading full entities.

### How I proved it works
8 integration tests through the real HTTP stack against real PostgreSQL: search finds the
right train with correct distance/duration/per-class availability; the wrong direction
returns an empty page; missing/blank/invalid inputs return 400 with the right error code;
availability lists coaches and can be filtered by class; an unknown schedule returns 404.

### What's next
Phase 4 — the hard part: booking seats correctly under heavy concurrency (hold → confirm,
idempotency keys, optimistic vs pessimistic locking, and the scheduled job that frees
expired holds).

---

## Phase 4 — Seat Booking Under Concurrency (the hard part)

### What I built
- Two-phase booking: **HOLD** seats (with a time limit), then **CONFIRM**.
- **Two interchangeable locking strategies** behind one interface, both proven never to overbook.
- **Idempotency** so a retried request never books twice.
- A background job that frees seats from holds that were never confirmed.

### The core problem restated
Many people, one seat, real money. Two people must NEVER get the same seat, even with hundreds
hitting the system in the same millisecond. I defend this at three layers:
1. **The database** (Phase 2): a CHECK that "seats left" can't go negative, and a partial unique
   index so one seat can't have two live claims.
2. **The application**: a locking strategy on the seat counter.
3. **Retries**: bounded, so a momentary clash doesn't fail a request that should have succeeded.

### Key decisions

**1. The booking is created at HOLD time; its id IS the hold ticket.**
Holding seats immediately creates a booking in state `HELD`, one seat-hold row per seat (each
with an expiry time), and the passenger records. The booking's id is the `holdId` I return.
Confirm just flips `HELD → CONFIRMED`. Benefit: no separate "hold group" table — the booking
naturally groups the held seats.

**2. Two locking strategies behind one interface (`SeatLockStrategy`).**
Both adjust the per-coach "seats available" counter safely:
- *Optimistic* (`@Version`): read the counter, change it, and let a version number catch anyone
  who changed it in between. On a clash, retry the whole hold. Great when clashes are rare — no
  locks held while a user is deciding.
- *Pessimistic* (`SELECT … FOR UPDATE`): lock the counter row first, so everyone else queues. No
  retries, but the lock is held for the transaction. Great under heavy contention.
Same interface, swappable by config — the headline comparison for an interview.

**3. One transaction wraps the counter change AND the seat holds.**
Reserving means (a) decrement the counter and (b) insert the seat-hold rows — in ONE
transaction. If the seat was already taken, the hold-insert fails on the unique index, the whole
transaction rolls back, and the counter decrement is undone automatically. That is why there is
never a "phantom" reservation (counter down but no actual hold).

**4. Retry the WHOLE operation, in a fresh transaction.**
With optimistic locking, once a version clash happens the transaction is poisoned and cannot
continue — so the retry re-runs the entire hold in a brand-new transaction. To make Spring start
a new transaction each attempt, the transactional work lives in a *separate bean*
(`BookingCommandExecutor`) that the retry loop (`BookingService`) calls through the proxy.
Retries are bounded, so a genuinely sold-out train fails fast instead of looping.

**5. Isolation level: READ_COMMITTED (the default), on purpose.**
I did NOT use SERIALIZABLE. Correctness comes from the version check (optimistic) or the row
lock (pessimistic) *plus* the database constraints — not from a heavy isolation level.
SERIALIZABLE would only add serialization failures to retry, for no extra safety here. Being
able to explain this trade-off is the point of the exercise.

**6. Idempotency keys.**
The client sends an `Idempotency-Key` header, stored on the booking with a unique constraint. If
the same key returns (a retry), I return the SAME hold instead of booking again. Even the race —
two identical requests at once — is handled: the loser catches the unique-key violation and
retries, then its idempotency pre-check replays the winner's result. A retried request can never
double-book.

**7. Expiring stale holds.**
A scheduled sweep finds holds past expiry, returns their seats to the counter, and marks the
booking `EXPIRED`. The subtle bit is the race with confirm — a hold could be confirmed at the
exact moment the sweep tries to expire it. The booking's **version number** mediates: confirm and
expiry both update the booking and check the version, so only one wins. If expiry loses, it skips
(already confirmed); if confirm loses, it retries and sees the hold is gone. Each booking is
expired in its own transaction so one contested booking doesn't stall the sweep.

### How I proved it (the key test)
A test launches **40 threads** with a `CountDownLatch` so they all fire at the same instant, all
trying to book a coach with only **8 seats** left. It asserts:
- exactly 8 succeed, the other 32 are cleanly rejected ("sold out"),
- the counter ends at exactly 0 (never negative, no lost updates),
- there are exactly 8 live holds — one per seat.
It runs for BOTH strategies. Plus tests for idempotency (same key → same hold), the full
hold→confirm flow over HTTP, input validation, missing-auth (401), and the expiry sweep.

### Trade-off notes (optimistic vs pessimistic)
- **Optimistic** wins when clashes are rare: no waiting, high throughput. Under very heavy
  contention it can "retry-storm", so retries are bounded.
- **Pessimistic** wins under heavy contention: no wasted work, predictable — but it serializes
  everyone on that coach behind a row lock.
- The database guarantees mean *neither* can overbook — the choice is about performance, not safety.

### What's next
Phase 5 formalizes the booking lifecycle as a state machine (illegal transitions become impossible
in code), adds cancellation with fare-rule-based refunds (Strategy pattern), and handles waitlist
promotion when a confirmed booking is cancelled.

---

## Phase 5 — Booking Lifecycle

### What I built
- A **state machine** that makes illegal booking transitions impossible in code.
- **Cancellation** with a refund from a swappable rule (Strategy pattern).
- **Waitlist promotion** when a confirmed booking is cancelled, with the race condition handled.
- The booking read/cancel endpoints: list my bookings (paginated), get one by PNR, cancel by PNR.

### Key decisions

**1. A real state machine, not scattered if-checks.**
A booking moves through states (HELD → CONFIRMED/EXPIRED/CANCELLED, WAITLISTED → CONFIRMED, …). I
defined the allowed moves in ONE place (`BookingStateMachine`) and made the only way to change
status be `booking.transitionTo(newStatus)`, which checks the table and throws on an illegal move.
So "confirm an already-cancelled booking" simply cannot happen — the code refuses it. A unit test
proves illegal transitions throw. Far safer than sprinkling `if (status == …)` around the codebase.

**2. Refund rules via the Strategy pattern.**
How much you get back depends on how close to departure you cancel. That lives behind a
`RefundPolicy` interface with a default tiered implementation (90% early, down to 0% near
departure). Being an interface, I can swap the policy (per fare class, promos, …) without touching
the cancellation flow. Cancellation records the refund as a REFUNDED payment row (the real gateway
refund is wired in Phase 7).

**3. Waitlist promotion, and its race condition.**
When a confirmed booking is cancelled, its seat frees up and the first waitlisted booking for that
coach should be promoted and given the seat. The dangerous case: two confirmed bookings cancelled
at the same instant both try to promote the SAME waitlisted booking — it could end up with two
seats. I handle this with the waitlisted booking's **version number**: both promotions load it,
both try to confirm it, but only the first `save` wins; the second sees the version changed, is
rejected, rolls back (undoing its seat grab) and moves on. A test cancels two confirmed bookings
concurrently and asserts the waitlisted booking ends up with exactly one seat. Each promotion runs
in its own transaction (after the cancel commits) so one contested promotion can't undo the cancel
or block the others.

**4. Owner isolation on reads.**
You can only see or cancel your own bookings; a request for someone else's returns 404 (not 403),
so the system doesn't even reveal that the booking exists. Phase 6 adds real login and an ADMIN
override.

### Design note: waitlist creation
The fixed API is seat-selection based, so there is no public "join the waitlist" endpoint. The
Phase 5 requirement is the *promotion* logic and its race handling, wired into cancellation and
fully tested; waitlisted bookings are created internally (in a full product this would be the
"book any seat / join waitlist" flow). To support seatless waitlisted passengers, a passenger's
seat is nullable until promotion assigns one.

### How I proved it
Unit tests for the state machine and refund tiers; integration tests for list/fetch (with a
non-owner getting 404), cancel-refunds-90%-and-frees-the-seat, cancel via DELETE, and single
promotion on cancel; plus a concurrency test where two simultaneous cancellations promote the
waitlist exactly once.

### What's next
Phase 6: real security — JWT login/refresh, BCrypt passwords, USER/ADMIN roles, and method-level
authorization (including the test that user A can't touch user B's booking, now enforced by the
security layer).

---

## Phase 6 — Security (Authentication & Authorization)

### What I built
- **Register / login / refresh** endpoints that hand out **JWTs** (JSON Web Tokens).
- **Stateless** security: the server keeps no session; every request carries its own proof.
- **BCrypt**-hashed passwords (never stored in plain text).
- **Two roles** (USER, ADMIN) with admin-only endpoints, enforced in two places.
- The proof that **user A cannot see or cancel user B's booking**, now enforced by the login layer.

### Key decisions

**1. Stateless JWT instead of server-side sessions.**
When you log in, the server gives you a signed token that says "you are user 42, role USER". On
every later request you send that token in the `Authorization: Bearer …` header, and the server
*verifies the signature* rather than looking anything up. Nothing about you is stored on the
server between requests.
- Why: it scales horizontally with no shared session store — any instance can serve any request,
  which is exactly what a bank running many copies of a service behind a load balancer needs.
- Rejected alternative: HTTP sessions (a `JSESSIONID` cookie + server-side session). Simpler for a
  single server, but needs sticky sessions or a shared session cache once you run more than one.

**2. Two token types: a short access token and a long refresh token.**
The access token lives ~15 minutes; the refresh token ~14 days. You call protected endpoints with
the access token; when it expires you exchange the refresh token for a fresh pair at `/auth/refresh`.
- Why: it limits the damage if an access token leaks (it dies quickly) without forcing the user to
  re-type their password every 15 minutes.
- The subtle bug I designed out: an access token must not be usable *as* a refresh token, and vice
  versa. Each token carries a `type` claim (`access`/`refresh`). The resource server only accepts
  `type=access`; the refresh endpoint only accepts `type=refresh`. There's a test for each direction.

**3. I used Spring's OAuth2 *resource server*, not a hand-written filter.**
Rather than write my own "read the header, parse the token, check it" servlet filter (the common
tutorial approach, and easy to get subtly wrong), I configured Spring Security's resource-server
support with a Nimbus HS256 decoder. Spring does the parsing, signature check, and expiry check;
I only map the token's `role` claim to a Spring authority and its `sub` claim to the user id.
- Why: security-critical parsing is exactly the code you do *not* want to write by hand.
- HS256 (one shared secret) is fine here because the same service signs and verifies. If a separate
  service had to verify without being able to mint tokens, I'd switch to RS256 (public/private key).

**4. The `sub` claim IS the user id — so nothing else in the app had to change.**
Back in Phase 4 the "who is the caller?" question went through a small interface,
`CurrentUserProvider`, with a stub that read an `X-User-Id` header. In Phase 6 I deleted the stub
and added one backed by the security context that reads the user id from the verified token's `sub`
claim. **No controller changed.** That's the payoff of having put the seam there in Phase 4 — the
real security dropped in behind an interface the rest of the code already depended on.

**5. Authorization enforced at two layers (defence in depth).**
Admin endpoints are protected both by a URL rule (`/api/v1/admin/**` needs role ADMIN) *and* by a
method-level `@PreAuthorize("hasRole('ADMIN')")` on the controller. Either alone would do; having
both means a future routing mistake can't silently expose an admin action.

**6. Ownership checks return 404, not 403.**
If user A asks for user B's booking, the answer is **404 Not Found**, not 403 Forbidden. A 403
would confirm the booking exists; 404 reveals nothing. This ownership rule lives in the query
service (Phase 5) and is now exercised through the real login layer.

**7. Errors from the security layer use the same JSON envelope as everything else.**
A rejected request doesn't get Spring Security's default HTML/blank error — a custom
`AuthenticationEntryPoint` (401, `UNAUTHENTICATED`) and `AccessDeniedHandler` (403, `ACCESS_DENIED`)
write the same `{success,error:{code,…}}` shape the rest of the API uses, so the frontend handles
auth failures identically to every other error.

### How I proved it (and how I tested through real security)
- **`MockJwt` test helper.** The existing booking tests used to fake the caller with an `X-User-Id`
  header. That header no longer exists. Instead of minting and signing a real token in every test,
  the helper stamps the request with an already-authenticated JWT
  (`.with(MockJwt.user(id))` / `.admin(id)`) that runs through the genuine security filter chain.
  Fast, and still exercises the real authorization rules.
- **`AuthIntegrationTest`** drives the *real* flow end to end: register → 201; login mints genuine
  HS256 tokens; refresh exchanges them; a real `Bearer` token (decoded by the actual Nimbus resource
  server) opens a protected endpoint; no token → 401; wrong password → 401; and both token-confusion
  directions are rejected.
- **`AuthorizationIntegrationTest`** is the spec-required proof: user A gets 404 reading *and*
  cancelling user B's booking (and B's booking is verified untouched afterwards); a USER hitting an
  admin endpoint gets 403; an ADMIN gets 200; and an unauthenticated admin call gets 401.
- Full suite: **51 tests green** (was 37).

### What's next
Phase 7: payments. Confirm a booking only after a mock payment gateway succeeds, and handle the
nasty failure paths (gateway timeout, "charged but our commit failed", duplicate callbacks) with
compensating actions — a **saga**. The failure paths are tested with WireMock.

---

## Phase 7 — Payments (the confirm saga)

### What I built
- A booking is now **confirmed only after a real payment charge succeeds**.
- A **payment gateway** behind a small interface, with a real HTTP adapter and a co-located **mock
  provider** so the whole thing runs locally end to end.
- The confirm flow is a **saga** — a sequence of steps, each with a compensating action — because it
  spans two systems (our database and the payment provider) that can't share one transaction.
- Every failure path handled: **declined** charge, gateway **timeout**, gateway **5xx**, and
  **"charged but we couldn't confirm"** (which triggers an automatic **refund**).
- Duplicate confirms **never double-charge**.

### The core problem: two systems, no shared transaction
Confirming does two things that must both happen or neither: **take the money** (at the external
provider) and **mark the booking CONFIRMED** (in our database). These live in different systems, so
a single database transaction cannot cover both — and a distributed two-phase commit across an
HTTP payment API is impractical and not what real systems do. The industry answer is a **saga**: do
the steps in order, and if a later step fails, run a **compensating action** to undo the earlier
ones. Here the compensation for "charged but not confirmed" is **refund the charge**.

### Key decisions

**1. The remote charge happens OUTSIDE any database transaction.**
The single most important structural decision. If I called the gateway from inside a `@Transactional`
method, a slow or hung provider would hold a database connection (and any row locks) open for the
whole call — under load that exhausts the connection pool and cascades into an outage. So the saga
is orchestrated in a **non-transactional** method (`BookingService.confirm`) that calls three
*separate* short transactions on either side of the network call:
1. `prepareConfirm` (tx) — validate the hold, snapshot the PNR and fare.
2. `charge` (no tx) — call the provider.
3. `finalizeConfirm` (tx) — only on success, flip to CONFIRMED and record the payment atomically.

**2. A port/adapter for the gateway; only the interface leaks into the domain.**
The booking code depends on a tiny `PaymentGateway` interface (`charge`, `refund`), never on HTTP.
The real `HttpPaymentGateway` uses Spring's `RestClient` with **explicit connect and read timeouts**
so a hung provider fails fast as a `PaymentGatewayException` instead of pinning a thread. In tests I
swap the adapter for a fake, or point the *real* adapter at WireMock — the domain code is identical.

**3. A declined charge and a technical failure are different things.**
- A **decline** (insufficient funds) is a normal business answer: the gateway returns 200 with
  `status=DECLINED`. We record a FAILED payment and tell the user; the booking stays HELD so they
  can retry or let it expire. No exception "escapes" as a 500.
- A **technical failure** (timeout, 5xx, connection refused) is thrown as `PaymentGatewayException`.
  Crucially the outcome is **indeterminate** — money may or may not have moved — so we do *not*
  confirm, and any later compensation errs on the side of refunding.

**4. Idempotency = no double charge, at two levels.**
The charge sends the **booking PNR as an idempotency key**. A real provider dedupes on that key, so
even if the same charge is sent twice (a client retry, a network blip) it captures once and returns
the same reference — the mock provider models exactly this. On top of that, `prepareConfirm`
short-circuits an already-CONFIRMED booking *before* charging, so a duplicate confirm is a pure
replay. A test asserts a second confirm sends **zero** additional charges to the gateway.

**5. The compensation: charged but couldn't confirm → refund.**
The scary window is: the charge succeeded, but `finalizeConfirm` fails — e.g. the hold expired in
the split second between prepare and finalize, or repeated optimistic conflicts with the expiry
sweep exhaust the retry budget. If I did nothing, we'd have taken money for a booking we never
confirmed. Instead the saga catches the failure and runs the **compensating action**: `refund` the
charge (best-effort, logged for manual reconciliation if the refund itself fails) and record a
REFUNDED payment. The user gets a clear error *and* their money back.

**6. The mock provider is a deliberately separate, decoupled component.**
It shares no code with the booking domain and keeps its own in-memory ledger, so it behaves like an
external service and could be lifted into its own deployable unchanged. It's only for local/Docker
runs; the automated tests use WireMock instead. A manual demo can steer it with the payment token
(`…decline…` declines, `…fail…` returns a 502).

### How I proved it
- **Unit test of the saga** (`ConfirmSagaUnitTest`, gateway + executor mocked) pins down the branches
  that are awkward to force live: approve, already-confirmed (no charge), decline (records FAILED, no
  confirm), technical failure (no confirm), and the **charged-but-finalize-fails → refund**
  compensation. It also asserts the charge uses the PNR as its idempotency key.
- **WireMock integration test** (`PaymentSagaIntegrationTest`) drives the *real* `HttpPaymentGateway`
  over HTTP against a stubbed provider and checks the end-to-end result in the database: approve →
  CONFIRMED + SUCCESS payment; decline → still HELD + FAILED payment; **timeout** (WireMock fixed
  delay beyond the read timeout) → still HELD, no success payment; **500** → still HELD; and a
  duplicate confirm → exactly **one** charge on the wire.
- The rest of the suite keeps its speed by using an in-process approving fake gateway, so the happy
  path still exercises the full saga without a network call. Full suite: **62 tests green** (was 51).

### Trade-off notes (saga vs. two-phase commit)
A distributed transaction (XA/2PC) *could* make the DB commit and the charge atomic, but payment
APIs don't participate in XA, 2PC needs a blocking coordinator that harms availability, and it locks
resources across the network. A saga trades strict atomicity for **eventual consistency plus
compensation**, which is how real payment systems work — and it keeps each step's transaction short.

### What's next
Phase 8: broaden the test suite, make the concurrency test rigorous (zero overbooking under ~500
simultaneous requests, not 40), add JaCoCo coverage on the booking/payment core, and a k6 load
script against the booking endpoint.

---

## Phase 8 — Testing & Load

### What I built
- A **rigorous overbooking test**: ~500 requests fired at once for a limited seat pool, proving each
  seat is sold exactly once.
- More **unit tests** for the small pure pieces (fare calculation, PNR generation).
- **JaCoCo** coverage measurement with an enforced **80% floor on the booking engine**, wired into
  the build so it fails if coverage drops.
- A **k6 load script** that drives the search/availability/booking endpoints.

### Key decisions

**1. The headline test now uses ~500 simultaneous requests, and virtual threads.**
The whole project is "never sell a seat twice", so the proof has to be convincing. The Phase 4 test
used 40 threads; this one uses **500 requests released at the same instant** (a `CountDownLatch`
holds them all at the gate, then one `countDown` frees them together) contending for **50** seats. It
asserts exactly 50 succeed, 450 are cleanly rejected, the counter ends at exactly 0 (never negative,
no lost updates), and there are exactly 50 live holds.
- I used Java 21 **virtual threads** (`newVirtualThreadPerTaskExecutor`) so 500 concurrent holds
  don't need 500 OS threads — a neat fit for this "lots of threads mostly waiting on I/O" workload.
- I ran it with the **pessimistic** strategy: under this much contention a row lock serialises the
  contenders cleanly, whereas the optimistic strategy would spend effort on retries. This is the
  concrete "which strategy under heavy contention?" answer from Phase 4, now demonstrated.

**2. A high coverage bar, but only where it means something.**
I added JaCoCo and enforce **80% line coverage on the booking engine** packages (`booking.service`,
`booking.lock`, `booking.domain`, `booking.refund`) — the code where a bug costs money or oversells.
The build's `check` task depends on the verification, so coverage can't silently rot. I deliberately
did **not** demand 80% everywhere: DTOs, config records, the app entrypoint, and the throwaway mock
payment provider are not logic worth chasing coverage on, and a blanket number would just invite
meaningless tests. Measured coverage on the enforced packages is ~85–100%.

**3. Unit-test the pure pieces directly, integration-test the rest.**
Fare calculation and PNR generation are pure functions, so they get fast plain-JUnit tests (no Spring
context) — including a PNR test that generates 10,000 codes and asserts no collisions and no
ambiguous characters. The stateful, concurrency-sensitive code stays covered by the integration
tests against real PostgreSQL, where the behaviour that matters actually happens.

**4. A k6 script that models real traffic shape.**
The load script (`load/k6/booking-flow.js`) authenticates, then drives the **read-heavy** path a
booking site actually serves — search and availability — with **thresholds** that fail the run if
p95 latency or the error rate regress. The **write** path (hold → confirm) is included but gated
behind a supplied seat pool, because every hold consumes a distinct seat; you can't loop the same
seat. That honesty (documented in the load README) is the point: a naive booking load test that
reuses one seat would just measure "sold out" responses.

### How I proved it
- The 500-request test passes for the pessimistic strategy with zero overbooking.
- `./gradlew check` runs the full suite **and** the coverage gate; both green. **68 tests.**
- The k6 script runs against a seeded local stack and reports search/availability latency and
  booking error rate against its thresholds.

### What's next
Phase 9: observability — custom Micrometer metrics (bookings/sec, hold-expiry rate, lock-retry
count, payment-failure rate), and structured JSON logs with a correlation id threaded through via
MDC so one request can be followed across log lines.

---

## Phase 9 — Observability

### What I built
- **Custom business metrics** through Micrometer, scrapeable by Prometheus at `/actuator/prometheus`.
- A **correlation id** on every request, in the logs and echoed on the response.
- **Structured JSON logging** (opt-in) that automatically carries the correlation id.
- The correlation id is **propagated to the payment provider**, so one trace spans both services.

### Key decisions

**1. Metrics that answer operational questions, not just "how many HTTP 200s".**
Spring already reports generic request metrics; I added the ones specific to *this* business, as
Micrometer counters: holds created, **bookings confirmed** (→ bookings/sec), **holds expired**,
**lock retries** (how much optimistic contention we're actually paying for), and **payment failures**
(→ failure rate). They live in one `BookingMetrics` component and are incremented at the exact points
in `BookingService` where those events happen. The monitoring system turns the counters into rates;
I don't precompute rates in the app. This is what lets an on-call engineer see "payment failures
just spiked" or "lock-retry rate climbed after that deploy".

**2. A correlation id per request, set once, cleared always.**
A servlet filter (`CorrelationIdFilter`) runs **first** (highest precedence, before security) and
puts a correlation id into the logging **MDC** (mapped diagnostic context). Every log line for that
request then automatically prints the id, so you can grep one request's entire journey out of an
interleaved log. It honours an inbound `X-Correlation-Id` (so a gateway or the frontend can set it)
but **sanitises** it first — only `[A-Za-z0-9._-]`, capped length — because an unvalidated header
copied into logs is a log-injection/forgery vector. The id is echoed on the response so a user can
quote it in a bug report, and the MDC is cleared in a `finally` so it never leaks onto the next
request that reuses the pooled thread.

**3. Structured JSON logging via Boot's built-in support, opt-in.**
Rather than hand-roll a Logback JSON layout, I used Spring Boot's native structured logging: set
`LOG_STRUCTURED_FORMAT=ecs` and every line becomes JSON (Elastic Common Schema) with the MDC fields —
including `correlationId` — as first-class properties a log system can index. It's left **off by
default** so local development keeps human-readable console logs; production turns it on. Knowing the
framework already does this (and when to use it) is the point.

**4. Propagating the trace across the service boundary.**
When the confirm saga calls the payment provider, the `HttpPaymentGateway` copies the current
correlation id onto the outgoing request as `X-Correlation-Id`. So a single id ties together our API
logs *and* the provider's — the beginning of distributed tracing, done the cheap way without a full
tracing backend. (Micrometer Tracing/OpenTelemetry would formalise this later; the seam is here.)

### How I proved it
- `CorrelationIdFilterTest` (unit): generates an id when absent, honours a valid inbound one, rejects
  a hostile header (no `INJECTED`, no newline) and generates fresh, and clears the MDC afterwards.
- `ObservabilityIntegrationTest`: every response carries a well-formed `X-Correlation-Id`; an inbound
  id is echoed back; and confirming a booking increments the `railreserve.bookings.held` and
  `railreserve.bookings.confirmed` counters by exactly one (read as a before/after delta on the live
  `MeterRegistry`).
- Full suite green: **75 tests**.

### What's next
Phases 10–12: the frontend — a Vite + React + TypeScript + Tailwind app with a typed API client, JWT
auth with refresh-on-401, protected routes, the booking pages (search → seat selection → passengers
→ payment with a hold countdown → my bookings), an admin view, and polish (skeletons, error
boundaries, toasts, empty states, mobile) with React Testing Library tests.

---

## Phase 10 — Frontend Foundation (setup, API client, auth)

### What I built
- A **Vite + React + TypeScript + Tailwind** app in `frontend/`.
- A **typed API client** whose types mirror the backend contract exactly.
- **JWT auth** with automatic, transparent **refresh-on-401**.
- An **auth context** and **protected routes** (including an admin-only gate).

### A small backend addition first
The seat-selection page needs to know *which specific seats* are free, but the availability API only
returned per-class counts. I added one read endpoint — `GET /schedules/{id}/coaches/{coachId}/seats`
— returning each seat with an `available` flag (a seat is taken if it has a live hold or confirmed
booking), and added `coachId` to the coach-availability projection so the UI can navigate to it. Two
integration tests cover it. The UI treats "available" as advisory: the real overbooking guarantees
still live in the database at hold time, so a seat shown free can still be lost to a concurrent
booker — and the hold request handles that cleanly.

### Key decisions

**1. The API types are a hand-kept mirror of the backend DTOs, in one file.**
`api/types.ts` declares TypeScript interfaces for every response the backend sends. Because every
call is typed, a contract change (a renamed field, a new enum value) surfaces as a **compile error**
across the whole UI instead of a runtime surprise. The envelope (`ApiResponse<T>`) is unwrapped in
one place, which also normalises errors into a single `ApiRequestError` type carrying the backend's
machine code and field errors.

**2. Refresh-on-401 is transparent and handles the thundering herd.**
An axios response interceptor catches a 401, calls `/auth/refresh` once, and retries the original
request with the new token — the user never sees it. The subtle part: if *many* requests 401 at the
same moment (an access token that just expired), they must not each fire a refresh. A single shared
`refreshPromise` means exactly one refresh happens and every queued request retries with its result.
The refresh call uses a bare axios instance so it can't recurse through the interceptor, and if
refresh itself fails, the tokens are cleared and the user falls back to the login screen.

**3. Tokens live in one store that React subscribes to.**
`tokenStorage` is the single source of truth (localStorage + a subscription). The auth context
subscribes, so when the interceptor rotates tokens — or another browser tab logs out — the UI's
notion of "who's logged in" updates automatically. The context decodes the JWT purely for display
and the admin gate; it never trusts it for security (that's the server's job).

**4. Route protection driven by the context.**
`ProtectedRoute` redirects an unauthenticated user to `/login`, remembering where they were headed so
login can send them back; an `requireAdmin` flag additionally gates the admin area. The same JWT
`role` claim the backend enforces is what the UI reads to show/hide admin — defence lines that agree.

---

## Phase 11 — Frontend Booking Flow (the pages)

### What I built
The full booking journey plus account and admin screens:
search → **seat selection** → **passenger details (creates the hold)** → **payment (with a live hold
countdown)** → confirmation, then **my bookings** (with cancel), a **booking detail** view, and a
read-only **admin** bookings table.

### Key decisions

**1. The flow mirrors the backend's two-phase model exactly.**
The seats are **held** when the user submits passenger details, and **confirmed** on payment. So the
hold's TTL is real by the time the user reaches payment — the page shows a **countdown** to
`expiresAt`, turns red under a minute, and once it hits zero disables payment and offers to start
over. The UI isn't pretending; it's showing the same hold lifecycle the server enforces.

**2. The hold request carries an idempotency key.**
The passengers page generates a UUID once (stable across re-renders) and sends it as the
`Idempotency-Key`. If the user double-clicks or a response is lost and retried, the backend returns
the *same* hold instead of grabbing a second set of seats — the client half of the idempotency
guarantee built in Phase 4.

**3. Seat selection is a real grid backed by real seat ids.**
It loads the coach list, then the seat map for the chosen coach, and renders a grid where free seats
are selectable, taken seats are disabled, and selection is capped. The chosen **seat ids** (not just
numbers) flow through to the hold request, so the booking targets exactly those berths.

**4. State moves through the flow via router navigation state, with guards.**
Each step hands the next the data it needs through React Router's location state. If someone deep-links
into `/book/payment` without a hold, the page has nothing to act on and redirects home — no
half-initialised screens.

---

## Phase 12 — Frontend Polish & Tests

### What I built
- **Loading, empty, and error states** everywhere data is fetched.
- **Toasts**, an **error boundary**, and accessible, mobile-friendly components.
- **React Testing Library** tests for the pieces most likely to break.

### Key decisions

**1. Every async screen has three states, not one.**
Lists and detail pages render **skeletons** while loading, a purpose-built **empty state** (with a
next action, e.g. "Find a train") when there's nothing, and an inline **error** with the server's
message when a call fails. The default "blank screen then pop-in" is avoided on purpose — it's the
difference between a demo and something that feels finished.

**2. Failures are visible and contained.**
A top-level **error boundary** catches any render-time crash and shows a recover-by-reload card
instead of a white screen. Transient failures (a declined payment, a cancellation error) raise a
**toast** with the backend's own message, so the user always learns *why*.

**3. Accessibility and mobile are built in, not bolted on.**
Forms use real `<label>`s, the seat grid exposes `aria-pressed`/`aria-label` per seat, toasts use an
`aria-live` region, and layouts are responsive (the search form, seat grid, and booking cards reflow
on small screens). A restrained palette — slate neutrals with a single teal accent, no gradients or
emoji — keeps it looking like a tool a bank would ship.

**4. Tests target behaviour and the risky seams.**
React Testing Library tests assert what a user experiences: login submits credentials and surfaces a
server error; search renders matching trains and an empty state; the seat grid toggles a free seat
and disables a taken one. Plus unit tests for the token store, the countdown hook (with fake timers),
and the formatting helpers. They test behaviour through the DOM, not implementation details, so they
survive refactors. **12 frontend tests**, and `npm run build` (typecheck + bundle) is clean.

### The project, end to end
RailReserve is now a full-stack system: a concurrency-safe, saga-driven Spring Boot backend
(**77 tests**, real PostgreSQL, an 80% coverage gate on the booking engine) behind a typed, polished
React frontend (**12 tests**). The through-line of the whole build is the one hard problem — never
sell a seat twice, never take money for a seat you didn't sell — defended in depth from the database
constraints up to the countdown the user watches on the payment screen.

---

## Phase 13 — Deployment & CI

### What I built
- **Multi-stage Dockerfiles** for the backend and the frontend.
- One `docker compose up --build` that starts the **whole stack** — database, API, and web UI.
- A **GitHub Actions** pipeline: build → unit tests → integration tests → Docker image build.

### Key decisions

**1. Multi-stage builds keep the runtime images small and free of build tooling.**
- *Backend:* stage one is a full **JDK 21** image that runs `./gradlew bootJar`; stage two is a
  **JRE-only** image that just carries the finished jar and runs it as a **non-root** user. The
  build tools, Gradle caches, and source never reach the shipped image — smaller attack surface and
  smaller image. The Gradle files are copied before the source so the dependency-download layer is
  cached and only re-runs when the build script changes.
- *Frontend:* stage one is a **Node** image that type-checks and bundles with Vite; stage two is a
  tiny **nginx** image serving the static files. No Node runtime ships to production — the browser
  just gets HTML, CSS, and JS.

**2. One origin in production — nginx proxies the API.**
The frontend container's nginx serves the SPA *and* reverse-proxies `/api` to the `backend` service
on the compose network. So the browser talks to a single origin and there's **no CORS** in the
deployed setup (CORS only matters for the split dev servers). nginx also does SPA history-fallback
(`try_files … /index.html`) so a page reload on a client-side route still works.

**3. `docker compose up` orchestrates start-up order with health, not guesswork.**
Compose starts Postgres, waits for its **healthcheck** to pass, then starts the backend (whose own
`/actuator/health` healthcheck must pass), then the frontend. `depends_on: condition:
service_healthy` means each service only starts once the thing it needs is actually *ready*, not just
*started* — so there's no flaky "backend raced the database" boot. Postgres is published on host
**5433** by default (5432 is usually taken), while services talk to it as `postgres:5432` inside the
network.

**4. The CI pipeline runs the stages the brief asks for, in order, and fails fast.**
GitHub Actions on every push/PR to `main`:
- **backend** job — *compile* → *unit tests* (the pure, container-free tests) → *integration tests*
  (`./gradlew build`, where Testcontainers boots a real PostgreSQL on the runner) with the **JaCoCo
  coverage gate**, and assembles the jar; test/coverage reports are uploaded as artifacts.
- **frontend** job — type-check + bundle, then the Vitest suite.
- **docker** job — runs only **after both test jobs pass**, and builds both production images with
  Buildx (layer-cached), validating the Dockerfiles on every change. Images are built, not pushed —
  publishing to a registry is the one deploy step left as a deliberate no-op for a portfolio repo.

### How I proved it
- `./gradlew build` produces a single runnable boot jar (the extra "-plain" jar is disabled so the
  image copies one artifact unambiguously).
- Both images build locally (`docker build ./backend`, `docker build ./frontend`), and
  `docker compose up --build` brings the stack up with the frontend on `http://localhost:5173`
  talking to the API through nginx.

### The finished project
Thirteen phases: from a database that makes overbooking structurally impossible, through
concurrency-safe booking, a payment saga, security, testing, and observability, to a typed React
frontend — all containerised and gated by CI. Every decision is written up here in plain English,
which is the whole point: to be able to defend each one out loud.
