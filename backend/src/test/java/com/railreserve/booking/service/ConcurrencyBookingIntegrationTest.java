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
 * The core proof of the whole project: many threads released at the same instant all try to
 * grab the last few seats. Exactly the available number succeed, everyone else is rejected
 * cleanly, and the inventory counter never goes wrong -- run for BOTH locking strategies.
 *
 * <p>Set-up caps inventory below the seat count and gives each thread its own seat, so the
 * aggregate counter (the {@link com.railreserve.booking.lock.SeatLockStrategy}) is the single
 * binding constraint being stressed. A high retry budget is configured so the optimistic
 * strategy is not starved under this contention.
 */
@TestPropertySource(properties = {
        "railreserve.booking.scheduler-enabled=false",
        "railreserve.booking.max-lock-retries=50"
})
class ConcurrencyBookingIntegrationTest extends AbstractIntegrationTest {

    private static final int AVAILABLE_SEATS = 8;    // M
    private static final int CONCURRENT_USERS = 40;  // N

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;
    @Autowired private SeatInventoryRepository inventoryRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;

    @Test
    void optimisticStrategyNeverOverbooks() throws Exception {
        assertExactlyTheAvailableSeatsAreBooked("optimistic", "opt");
    }

    @Test
    void pessimisticStrategyNeverOverbooks() throws Exception {
        assertExactlyTheAvailableSeatsAreBooked("pessimistic", "pes");
    }

    private void assertExactlyTheAvailableSeatsAreBooked(String strategy, String suffix) throws Exception {
        BookableSchedule fixture = data.bookableSchedule(suffix, CONCURRENT_USERS, AVAILABLE_SEATS);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        try {
            CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
            CountDownLatch fire = new CountDownLatch(1);
            AtomicInteger booked = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < CONCURRENT_USERS; i++) {
                Long seatId = fixture.seatIds().get(i); // each user picks a distinct seat
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                            List.of(seatId), List.of(new PassengerRequest("Passenger", 30, Gender.MALE)));
                    try {
                        bookingService.hold(request, fixture.userId(), null, strategy);
                        booked.incrementAndGet();
                    } catch (ApiException rejectedCleanly) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            ready.await(); // wait until every thread is poised
            fire.countDown(); // release them all at once
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }

            assertThat(booked.get()).as("exactly the available seats are booked").isEqualTo(AVAILABLE_SEATS);
            assertThat(rejected.get()).as("everyone else is rejected cleanly")
                    .isEqualTo(CONCURRENT_USERS - AVAILABLE_SEATS);

            SeatInventory inventory = inventoryRepository
                    .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
            assertThat(inventory.getAvailableCount()).as("no seats left, never negative").isZero();
            assertThat(inventory.getBookedCount()).as("counter matches the holds, no lost updates")
                    .isEqualTo(AVAILABLE_SEATS);

            long activeHolds = seatHoldRepository.findAll().stream()
                    .filter(hold -> hold.getSchedule().getId().equals(fixture.scheduleId()))
                    .filter(hold -> hold.getStatus() == HoldStatus.ACTIVE)
                    .count();
            assertThat(activeHolds).as("one live hold per booked seat").isEqualTo(AVAILABLE_SEATS);
        } finally {
            pool.shutdownNow();
        }
    }
}
