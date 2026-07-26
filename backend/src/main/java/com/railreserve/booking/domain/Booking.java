package com.railreserve.booking.domain;

import com.railreserve.common.domain.AbstractEntity;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.user.domain.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for a reservation. Passengers are part of the aggregate (cascade + orphan
 * removal). The {@link Version} field enables optimistic locking on lifecycle transitions, and
 * {@link #transitionTo} routes every status change through {@link BookingStateMachine} so illegal
 * transitions cannot happen.
 */
@Entity
@Table(name = "booking")
public class Booking extends AbstractEntity {

    private String pnr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id")
    private Coach coach;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private BigDecimal totalFare;

    private String idempotencyKey;

    private Integer waitlistPosition;

    @CreationTimestamp
    private Instant createdAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingPassenger> passengers = new ArrayList<>();

    protected Booking() {
    }

    public Booking(String pnr, AppUser user, Schedule schedule, Coach coach, BookingStatus status,
                   BigDecimal totalFare, String idempotencyKey) {
        this.pnr = pnr;
        this.user = user;
        this.schedule = schedule;
        this.coach = coach;
        this.status = status;
        this.totalFare = totalFare;
        this.idempotencyKey = idempotencyKey;
    }

    public void addPassenger(BookingPassenger passenger) {
        passengers.add(passenger);
        passenger.setBooking(this);
    }

    /** Changes the booking status, enforcing the state machine; illegal transitions throw. */
    public void transitionTo(BookingStatus target) {
        BookingStateMachine.assertCanTransition(this.status, target);
        this.status = target;
    }

    public String getPnr() {
        return pnr;
    }

    public AppUser getUser() {
        return user;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Coach getCoach() {
        return coach;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    public void setTotalFare(BigDecimal totalFare) {
        this.totalFare = totalFare;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public void setWaitlistPosition(Integer waitlistPosition) {
        this.waitlistPosition = waitlistPosition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<BookingPassenger> getPassengers() {
        return Collections.unmodifiableList(passengers);
    }
}
