package com.railreserve.booking.service;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.common.exception.ApiException;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rigorous overbooking proof the spec asks for: ~500 requests released at the same instant
 * contend for a limited pool of seats, and the system must sell each seat exactly once — never one
 * more, never negative inventory. It uses the <b>pessimistic</b> strategy (a row lock serialises the
 * contenders with no retry storm), and Java 21 <b>virtual threads</b> so 500 concurrent holds don't
 * need 500 platform threads.
 *
 * <p>Kept separate from the smaller two-strategy {@link ConcurrencyBookingIntegrationTest} so it can
 * dial up contention without slowing every run's fast path.
 */
@TestPropertySource(properties = {
        "railreserve.booking.scheduler-enabled=false",
        "railreserve.booking.lock-strategy=pessimistic"
})
class HighContentionBookingIntegrationTest extends AbstractIntegrationTest {

    private static final int AVAILABLE_SEATS = 50;
    private static final int CONCURRENT_USERS = 500;

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;
    @Autowired private SeatInventoryRepository inventoryRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;

    @Test
    void fiveHundredSimultaneousRequestsNeverOverbook() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("mass", CONCURRENT_USERS, AVAILABLE_SEATS);

        CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                Long seatId = fixture.seatIds().get(i); // each request targets a distinct seat
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                            List.of(seatId), List.of(new PassengerRequest("Passenger", 30, Gender.MALE)));
                    try {
                        bookingService.hold(request, fixture.userId(), null);
                        booked.incrementAndGet();
                    } catch (ApiException rejectedCleanly) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            ready.await();      // every request poised
            fire.countDown();   // release them together
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        }

        assertThat(booked.get()).as("exactly the available seats are sold").isEqualTo(AVAILABLE_SEATS);
        assertThat(rejected.get()).as("everyone else is cleanly rejected")
                .isEqualTo(CONCURRENT_USERS - AVAILABLE_SEATS);

        SeatInventory inventory = inventoryRepository
                .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
        assertThat(inventory.getAvailableCount()).as("sold out, never negative").isZero();
        assertThat(inventory.getBookedCount()).as("counter matches sales, no lost updates")
                .isEqualTo(AVAILABLE_SEATS);

        long activeHolds = seatHoldRepository.findAll().stream()
                .filter(hold -> hold.getSchedule().getId().equals(fixture.scheduleId()))
                .filter(hold -> hold.getStatus() == HoldStatus.ACTIVE)
                .count();
        assertThat(activeHolds).as("one live hold per sold seat").isEqualTo(AVAILABLE_SEATS);
    }
}
