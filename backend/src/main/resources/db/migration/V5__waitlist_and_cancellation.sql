-- V5__waitlist_and_cancellation.sql
-- Waitlist support: a waitlisted booking has a queue position and, until it is promoted,
-- passengers that do not yet have a seat.

ALTER TABLE booking ADD COLUMN waitlist_position INT;
ALTER TABLE booking ADD CONSTRAINT ck_booking_waitlist_position
    CHECK (waitlist_position IS NULL OR waitlist_position >= 1);

-- Waitlisted passengers have no berth until promotion assigns one.
ALTER TABLE booking_passenger ALTER COLUMN seat_id DROP NOT NULL;

-- A booking targets exactly one coach (the class booked or waitlisted for). Storing it
-- makes waitlist queuing (per schedule+coach) and cancellation straightforward.
ALTER TABLE booking ADD COLUMN coach_id BIGINT;
ALTER TABLE booking ADD CONSTRAINT fk_booking_coach FOREIGN KEY (coach_id) REFERENCES coach(id);
CREATE INDEX idx_booking_coach ON booking(coach_id);
CREATE INDEX idx_booking_waitlist ON booking(schedule_id, coach_id, waitlist_position) WHERE waitlist_position IS NOT NULL;
