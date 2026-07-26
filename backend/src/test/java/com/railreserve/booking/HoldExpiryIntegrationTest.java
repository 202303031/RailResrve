package com.railreserve.booking;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingPassenger;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.domain.PassengerStatus;
import com.railreserve.booking.domain.SeatHold;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.booking.service.BookingService;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.Seat;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.scheduling.repository.SeatRepository;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.TestDataFactory;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HoldExpiryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private SeatInventoryRepository seatInventoryRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private AppUserRepository appUserRepository;

    /** Builds a HELD booking whose single hold has status {@code status} and expires at {@code expiresAt}. */
    private Booking heldBookingWithOneHold(BookableSchedule fixture, String pnr,
                                           BookingStatus bookingStatus, HoldStatus holdStatus, Instant expiresAt) {
        SeatInventory inventory = seatInventoryRepository
                .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
        inventory.reserve(1);
        seatInventoryRepository.saveAndFlush(inventory);

        AppUser user = appUserRepository.findById(fixture.userId()).orElseThrow();
        Schedule schedule = scheduleRepository.findById(fixture.scheduleId()).orElseThrow();
        Seat seat = seatRepository.findById(fixture.seatIds().get(0)).orElseThrow();

        Booking booking = new Booking(pnr, user, schedule, seat.getCoach(), bookingStatus, new BigDecimal("450.00"), null);
        booking.addPassenger(new BookingPassenger(seat, "Passenger", 30, Gender.MALE, PassengerStatus.CONFIRMED));
        bookingRepository.saveAndFlush(booking);

        SeatHold hold = new SeatHold(seat, schedule, user, expiresAt);
        hold.setBooking(booking);
        hold.setStatus(holdStatus);
        seatHoldRepository.saveAndFlush(hold);
        return booking;
    }

    @Test
    void expiredActiveHoldIsReleasedAndBookingMarkedExpired() {
        BookableSchedule fixture = data.bookableSchedule("exp", 2);
        Booking booking = heldBookingWithOneHold(fixture, "EXPPNR000001",
                BookingStatus.HELD, HoldStatus.ACTIVE, Instant.now().minusSeconds(120));

        int expired = bookingService.expireStaleHolds();

        assertThat(expired).isEqualTo(1);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        SeatInventory inventory = seatInventoryRepository
                .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
        assertThat(inventory.getAvailableCount()).as("seat returned to inventory").isEqualTo(2);
        assertThat(inventory.getBookedCount()).isZero();
    }

    @Test
    void confirmedHoldPastItsTtlIsNotExpired() {
        BookableSchedule fixture = data.bookableSchedule("exp2", 2);
        Booking booking = heldBookingWithOneHold(fixture, "EXPPNR000002",
                BookingStatus.CONFIRMED, HoldStatus.CONFIRMED, Instant.now().minusSeconds(120));

        int expired = bookingService.expireStaleHolds();

        assertThat(expired).isZero();
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }
}
