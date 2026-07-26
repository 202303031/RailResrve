package com.railreserve.booking.domain;

import com.railreserve.common.domain.AbstractEntity;
import com.railreserve.scheduling.domain.Seat;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A passenger on a booking. The seat is nullable: a waitlisted passenger has no berth until
 * promotion assigns one via {@link #assignSeat}.
 */
@Entity
@Table(name = "booking_passenger")
public class BookingPassenger extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    private String name;
    private int age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private PassengerStatus status = PassengerStatus.CONFIRMED;

    protected BookingPassenger() {
    }

    public BookingPassenger(Seat seat, String name, int age, Gender gender, PassengerStatus status) {
        this.seat = seat;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.status = status;
    }

    // Set by Booking.addPassenger to keep both sides of the relationship in sync.
    void setBooking(Booking booking) {
        this.booking = booking;
    }

    /** Assigns a berth to a previously seatless (waitlisted) passenger on promotion. */
    public void assignSeat(Seat seat) {
        this.seat = seat;
    }

    public Booking getBooking() {
        return booking;
    }

    public Seat getSeat() {
        return seat;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public Gender getGender() {
        return gender;
    }

    public PassengerStatus getStatus() {
        return status;
    }

    public void setStatus(PassengerStatus status) {
        this.status = status;
    }
}
