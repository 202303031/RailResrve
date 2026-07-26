package com.railreserve.booking.domain;

import com.railreserve.common.domain.AbstractEntity;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.Seat;
import com.railreserve.user.domain.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A temporary claim on a seat with a TTL. The DB partial unique index
 * {@code uq_seat_hold_live} guarantees at most one live (ACTIVE or CONFIRMED) hold per
 * seat, which is the per-seat structural guard against double-booking.
 */
@Entity
@Table(name = "seat_hold")
public class SeatHold extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    private HoldStatus status = HoldStatus.ACTIVE;

    @CreationTimestamp
    private Instant createdAt;

    protected SeatHold() {
    }

    public SeatHold(Seat seat, Schedule schedule, AppUser user, Instant expiresAt) {
        this.seat = seat;
        this.schedule = schedule;
        this.user = user;
        this.expiresAt = expiresAt;
        this.status = HoldStatus.ACTIVE;
    }

    public Seat getSeat() {
        return seat;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public AppUser getUser() {
        return user;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public void setStatus(HoldStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
