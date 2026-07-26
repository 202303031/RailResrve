package com.railreserve.domain;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingPassenger;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.PassengerStatus;
import com.railreserve.booking.domain.SeatHold;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.catalog.domain.Station;
import com.railreserve.catalog.domain.Train;
import com.railreserve.catalog.domain.TrainType;
import com.railreserve.catalog.repository.StationRepository;
import com.railreserve.catalog.repository.TrainRepository;
import com.railreserve.scheduling.domain.BerthType;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.Seat;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.domain.TravelClass;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.scheduling.repository.SeatRepository;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.domain.UserRole;
import com.railreserve.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database-layer guarantees that make overbooking structurally impossible,
 * plus a JPA aggregate round-trip. These constraints are the safety net beneath the
 * application-level locking that arrives in Phase 4.
 */
@Transactional
class SchemaConstraintsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private StationRepository stationRepository;
    @Autowired private TrainRepository trainRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private CoachRepository coachRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatInventoryRepository seatInventoryRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext private EntityManager em;

    private Seat seat;
    private Schedule schedule;
    private AppUser user;
    private SeatInventory inventory;

    @BeforeEach
    void setUp() {
        Station station = stationRepository.saveAndFlush(new Station("TST", "Test Central", "Testville"));
        Train train = trainRepository.saveAndFlush(new Train("99999", "Test Express", TrainType.EXPRESS));
        schedule = scheduleRepository.saveAndFlush(new Schedule(train, LocalDate.now().plusDays(1)));
        Coach coach = coachRepository.saveAndFlush(new Coach(schedule, "S1", TravelClass.SLEEPER, 1));
        seat = seatRepository.saveAndFlush(new Seat(coach, "1", BerthType.LOWER));
        user = appUserRepository.saveAndFlush(new AppUser("tester@example.com", "hash", "Tester", "9990001111", UserRole.USER));
        inventory = seatInventoryRepository.saveAndFlush(new SeatInventory(schedule, coach, 1));
        // Keep the setUp graph invisible to other tests -- @Transactional rolls it all back.
        assertThat(station.getId()).isNotNull();
    }

    @Test
    void twoLiveHoldsOnTheSameSeatAreRejectedByTheDatabase() {
        seatHoldRepository.saveAndFlush(new SeatHold(seat, schedule, user, Instant.now().plusSeconds(600)));

        SeatHold duplicate = new SeatHold(seat, schedule, user, Instant.now().plusSeconds(600));

        // The partial unique index uq_seat_hold_live makes a second live claim impossible.
        assertThatThrownBy(() -> seatHoldRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inventoryAvailableCountCannotGoNegative() {
        Long inventoryId = inventory.getId();

        // Bypass the Java-level guard and hit the DB CHECK (available_count >= 0) directly.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE seat_inventory SET available_count = -1 WHERE id = ?", inventoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bookingAggregatePersistsItsPassengers() {
        Booking booking = new Booking("PNRTST00000001", user, schedule, seat.getCoach(),
                BookingStatus.CONFIRMED, new BigDecimal("725.00"), null);
        booking.addPassenger(new BookingPassenger(seat, "Alice", 30, Gender.FEMALE, PassengerStatus.CONFIRMED));

        bookingRepository.saveAndFlush(booking);
        em.clear(); // force a real reload from the database, not the first-level cache

        Booking reloaded = bookingRepository.findByPnr("PNRTST00000001").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getTotalFare()).isEqualByComparingTo("725.00");
        assertThat(reloaded.getPassengers()).hasSize(1);
        assertThat(reloaded.getPassengers().get(0).getName()).isEqualTo("Alice");
        assertThat(reloaded.getPassengers().get(0).getSeat().getId()).isEqualTo(seat.getId());
    }
}
