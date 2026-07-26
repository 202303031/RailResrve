package com.railreserve.booking.service;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingPassenger;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.domain.PassengerStatus;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.TestDataFactory;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two confirmed bookings are cancelled at the same instant, both freeing a seat and both trying
 * to promote the single waitlisted booking. The booking's {@code @Version} must let exactly ONE
 * promotion win, so the waitlisted booking ends up with exactly one seat -- never two.
 */
class WaitlistRaceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestDataFactory data;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private SeatInventoryRepository seatInventoryRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private CoachRepository coachRepository;
    @Autowired private AppUserRepository appUserRepository;

    private String confirm(BookableSchedule fixture, int seatIndex) {
        HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(seatIndex)),
                List.of(new PassengerRequest("Traveller", 30, Gender.MALE)));
        Long holdId = bookingService.hold(request, fixture.userId(), null).holdId();
        bookingService.confirm(holdId, "tok", fixture.userId());
        return bookingRepository.findById(holdId).orElseThrow().getPnr();
    }

    private Long createWaitlisted(BookableSchedule fixture, String pnr) {
        AppUser user = appUserRepository.findById(fixture.userId()).orElseThrow();
        Schedule schedule = scheduleRepository.findById(fixture.scheduleId()).orElseThrow();
        Coach coach = coachRepository.findById(fixture.coachId()).orElseThrow();
        Booking booking = new Booking(pnr, user, schedule, coach, BookingStatus.WAITLISTED, new BigDecimal("450.00"), null);
        booking.setWaitlistPosition(1);
        booking.addPassenger(new BookingPassenger(null, "Waitlisted", 40, Gender.FEMALE, PassengerStatus.WAITLISTED));
        return bookingRepository.saveAndFlush(booking).getId();
    }

    @Test
    void concurrentCancellationsPromoteTheWaitlistedBookingExactlyOnce() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("wlrace", 2);
        String confirmedA = confirm(fixture, 0);
        String confirmedB = confirm(fixture, 1);
        Long waitlistId = createWaitlisted(fixture, "WLRACEWAIT01");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch fire = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (String pnr : List.of(confirmedA, confirmedB)) {
                futures.add(pool.submit(() -> {
                    fire.await();
                    bookingService.cancel(pnr, fixture.userId());
                    return null;
                }));
            }
            fire.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Booking waitlisted = bookingRepository.findById(waitlistId).orElseThrow();
        assertThat(waitlisted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        long confirmedHolds = seatHoldRepository.findByBookingId(waitlistId).stream()
                .filter(hold -> hold.getStatus() == HoldStatus.CONFIRMED)
                .count();
        assertThat(confirmedHolds).as("promoted onto exactly one seat, never two").isEqualTo(1);

        SeatInventory inventory = seatInventoryRepository
                .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
        assertThat(inventory.getBookedCount()).as("one seat held by the promoted booking").isEqualTo(1);
        assertThat(inventory.getAvailableCount()).as("the other freed seat stays available").isEqualTo(1);
    }
}
