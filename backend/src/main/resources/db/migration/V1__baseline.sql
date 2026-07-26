-- V1__baseline.sql
-- Flyway baseline for RailReserve.
--
-- The real domain schema (stations, trains, schedules, seats, bookings, ...) is
-- introduced in Phase 2 as V2. This baseline exists so that:
--   1. Flyway is exercised end-to-end from the very first phase, and
--   2. the pgcrypto extension is available for later migrations that need
--      gen_random_uuid() / cryptographic helpers.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
