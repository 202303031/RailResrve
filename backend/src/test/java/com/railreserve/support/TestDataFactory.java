package com.railreserve.support;

import com.railreserve.catalog.domain.RouteStop;
import com.railreserve.catalog.domain.Station;
import com.railreserve.catalog.domain.Train;
import com.railreserve.catalog.domain.TrainType;
import com.railreserve.catalog.repository.RouteStopRepository;
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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds domain graphs for integration tests. Registered explicitly via {@code @Import} on
 * {@code AbstractIntegrationTest}. Used inside transactional tests, so nothing it saves
 * leaks between tests.
 */
@Component
public class TestDataFactory {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final RouteStopRepository routeStopRepository;
    private final ScheduleRepository scheduleRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final AppUserRepository appUserRepository;

    public TestDataFactory(StationRepository stationRepository,
                           TrainRepository trainRepository,
                           RouteStopRepository routeStopRepository,
                           ScheduleRepository scheduleRepository,
                           CoachRepository coachRepository,
                           SeatRepository seatRepository,
                           SeatInventoryRepository seatInventoryRepository,
                           AppUserRepository appUserRepository) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.routeStopRepository = routeStopRepository;
        this.scheduleRepository = scheduleRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.appUserRepository = appUserRepository;
    }

    public Station station(String code) {
        return stationRepository.save(new Station(code, code + " Junction", code + " City"));
    }

    public Train train(String number, TrainType type) {
        return trainRepository.save(new Train(number, number + " Express", type));
    }

    public RouteStop routeStop(Train train, Station station, int order,
                               LocalTime arrival, LocalTime departure, int distanceKm) {
        return routeStopRepository.save(new RouteStop(train, station, order, arrival, departure, distanceKm));
    }

    public Schedule schedule(Train train, LocalDate date) {
        return scheduleRepository.save(new Schedule(train, date));
    }

    public Coach coach(Schedule schedule, String code, TravelClass travelClass, int totalSeats) {
        return coachRepository.save(new Coach(schedule, code, travelClass, totalSeats));
    }

    public Seat seat(Coach coach, String number, BerthType berthType) {
        return seatRepository.save(new Seat(coach, number, berthType));
    }

    public List<Seat> seats(Coach coach, int count) {
        List<Seat> created = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            created.add(seat(coach, String.valueOf(i), BerthType.LOWER));
        }
        return created;
    }

    public SeatInventory inventory(Schedule schedule, Coach coach, int availableCount) {
        return seatInventoryRepository.save(new SeatInventory(schedule, coach, availableCount));
    }

    public AppUser user(String email) {
        return appUserRepository.save(new AppUser(email, "hashed", "Test " + email, "9990000000", UserRole.USER));
    }

    /** {@link #bookableSchedule(String, int, int)} with all seats available. */
    public BookableSchedule bookableSchedule(String suffix, int seatCount) {
        return bookableSchedule(suffix, seatCount, seatCount);
    }

    /** {@link #bookableSchedule(String, int, int, LocalDate)} two days out. */
    public BookableSchedule bookableSchedule(String suffix, int seatCount, int availableCount) {
        return bookableSchedule(suffix, seatCount, availableCount, LocalDate.now().plusDays(2));
    }

    /**
     * Builds a complete, ready-to-book schedule: a two-station route, a schedule on
     * {@code journeyDate}, one Sleeper coach with {@code seatCount} seats, inventory whose
     * available count is {@code availableCount}, and a user. The {@code suffix} keeps natural
     * keys unique across calls in non-transactional tests. Capping {@code availableCount} below
     * {@code seatCount} lets a concurrency test make the aggregate counter (not per-seat
     * contention) the binding constraint.
     */
    public BookableSchedule bookableSchedule(String suffix, int seatCount, int availableCount, LocalDate journeyDate) {
        Station a = station(("A" + suffix).toUpperCase());
        Station b = station(("B" + suffix).toUpperCase());
        Train train = train("T" + suffix, TrainType.EXPRESS);
        routeStop(train, a, 1, null, LocalTime.of(6, 0), 0);
        routeStop(train, b, 2, LocalTime.of(9, 0), null, 300);
        Schedule schedule = schedule(train, journeyDate);
        Coach coach = coach(schedule, "C1", TravelClass.SLEEPER, seatCount);
        List<Seat> seats = seats(coach, seatCount);
        inventory(schedule, coach, availableCount);
        AppUser user = user("user-" + suffix + "@example.com");
        return new BookableSchedule(schedule.getId(), coach.getId(),
                seats.stream().map(Seat::getId).toList(), user.getId());
    }
}
