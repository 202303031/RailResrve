package com.railreserve.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo/reference data. Deliberately kept OUT of Flyway migrations so that:
 *   - migrations stay pure schema (what runs in production is predictable), and
 *   - the integration tests, which run migrations against a fresh database, are not
 *     slowed down by ~100k seed rows.
 *
 * Enabled only when {@code railreserve.seed.enabled=true} (the docker profile sets it).
 * It is idempotent -- if stations already exist it does nothing -- and generates
 * schedules relative to {@code CURRENT_DATE} so the demo always has future journeys.
 */
@Component
@ConditionalOnProperty(name = "railreserve.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final int SCHEDULE_HORIZON_DAYS = 60;

    private final JdbcTemplate jdbc;

    public DataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM station", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Seed data already present ({} stations); skipping seeding.", existing);
            return;
        }
        log.info("Seeding reference data and {} days of schedules...", SCHEDULE_HORIZON_DAYS);
        seedStations();
        seedTrains();
        seedRoutes();
        seedSchedulesCoachesSeatsAndInventory();
        Integer seats = jdbc.queryForObject("SELECT count(*) FROM seat", Integer.class);
        log.info("Seeding complete: {} stations, {} trains, {} seats.",
                STATIONS.size(), TRAINS.size(), seats);
    }

    private void seedStations() {
        jdbc.batchUpdate("INSERT INTO station (code, name, city) VALUES (?, ?, ?)", STATIONS);
    }

    private void seedTrains() {
        jdbc.batchUpdate("INSERT INTO train (number, name, type) VALUES (?, ?, ?)", TRAINS);
    }

    private void seedRoutes() {
        ROUTES.forEach((trainNumber, stops) -> {
            for (int i = 0; i < stops.size(); i++) {
                Stop stop = stops.get(i);
                boolean isOrigin = (i == 0);
                boolean isTerminus = (i == stops.size() - 1);
                // Simple synthetic timetable: 2 hours between stops, 5-minute halts.
                LocalTime base = LocalTime.of(6, 0).plusHours(2L * i);
                LocalTime arrival = isOrigin ? null : base;
                LocalTime departure = isTerminus ? null : base.plusMinutes(5);
                jdbc.update("""
                        INSERT INTO route_stop (train_id, station_id, stop_order, arrival_time, departure_time, distance_km)
                        VALUES ((SELECT id FROM train WHERE number = ?),
                                (SELECT id FROM station WHERE code = ?),
                                ?, ?, ?, ?)
                        """, trainNumber, stop.stationCode(), i + 1, arrival, departure, stop.distanceKm());
            }
        });
    }

    private void seedSchedulesCoachesSeatsAndInventory() {
        // One schedule per train per day for the horizon.
        jdbc.update("""
                INSERT INTO schedule (train_id, journey_date, status)
                SELECT t.id, CURRENT_DATE + d, 'SCHEDULED'
                FROM train t CROSS JOIN generate_series(0, ?) AS d
                """, SCHEDULE_HORIZON_DAYS - 1);

        // Three coaches per schedule: Sleeper, 3-AC, 2-AC.
        jdbc.update("""
                INSERT INTO coach (schedule_id, coach_code, travel_class, total_seats)
                SELECT s.id, c.code, c.cls, c.seats
                FROM schedule s
                CROSS JOIN (VALUES ('S1', 'SL', 72), ('B1', '3A', 64), ('A1', '2A', 46)) AS c(code, cls, seats)
                """);

        // Seats per coach, with a repeating berth pattern.
        jdbc.update("""
                INSERT INTO seat (coach_id, seat_number, berth_type)
                SELECT co.id, n::text,
                       (ARRAY['LOWER','MIDDLE','UPPER','LOWER','MIDDLE','UPPER','SIDE_LOWER','SIDE_UPPER'])[((n - 1) % 8) + 1]
                FROM coach co
                CROSS JOIN LATERAL generate_series(1, co.total_seats) AS n
                """);

        // Inventory counter per (schedule, coach), fully available to start.
        jdbc.update("""
                INSERT INTO seat_inventory (schedule_id, coach_id, available_count, booked_count, version)
                SELECT co.schedule_id, co.id, co.total_seats, 0, 0
                FROM coach co
                """);
    }

    private record Stop(String stationCode, int distanceKm) {
    }

    private static List<Stop> route(Object... codeDistancePairs) {
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < codeDistancePairs.length; i += 2) {
            stops.add(new Stop((String) codeDistancePairs[i], (Integer) codeDistancePairs[i + 1]));
        }
        return stops;
    }

    // --- Reference data ---------------------------------------------------------------

    private static final List<Object[]> STATIONS = List.of(
            new Object[]{"NDLS", "New Delhi", "Delhi"},
            new Object[]{"BCT", "Mumbai Central", "Mumbai"},
            new Object[]{"CSTM", "Mumbai CSMT", "Mumbai"},
            new Object[]{"HWH", "Howrah Junction", "Kolkata"},
            new Object[]{"MAS", "Chennai Central", "Chennai"},
            new Object[]{"SBC", "KSR Bengaluru", "Bengaluru"},
            new Object[]{"ADI", "Ahmedabad Junction", "Ahmedabad"},
            new Object[]{"PUNE", "Pune Junction", "Pune"},
            new Object[]{"JP", "Jaipur Junction", "Jaipur"},
            new Object[]{"LKO", "Lucknow Charbagh", "Lucknow"},
            new Object[]{"PNBE", "Patna Junction", "Patna"},
            new Object[]{"BPL", "Bhopal Junction", "Bhopal"},
            new Object[]{"NGP", "Nagpur Junction", "Nagpur"},
            new Object[]{"SC", "Secunderabad Junction", "Hyderabad"},
            new Object[]{"BBS", "Bhubaneswar", "Bhubaneswar"},
            new Object[]{"GHY", "Guwahati", "Guwahati"},
            new Object[]{"TVC", "Thiruvananthapuram Central", "Thiruvananthapuram"},
            new Object[]{"ERS", "Ernakulam Junction", "Kochi"},
            new Object[]{"CBE", "Coimbatore Junction", "Coimbatore"},
            new Object[]{"MDU", "Madurai Junction", "Madurai"},
            new Object[]{"JU", "Jodhpur Junction", "Jodhpur"},
            new Object[]{"BKN", "Bikaner Junction", "Bikaner"},
            new Object[]{"ASR", "Amritsar Junction", "Amritsar"},
            new Object[]{"CDG", "Chandigarh", "Chandigarh"},
            new Object[]{"DDN", "Dehradun", "Dehradun"},
            new Object[]{"KOTA", "Kota Junction", "Kota"},
            new Object[]{"RTM", "Ratlam Junction", "Ratlam"},
            new Object[]{"JBP", "Jabalpur Junction", "Jabalpur"},
            new Object[]{"R", "Raipur Junction", "Raipur"},
            new Object[]{"VSKP", "Visakhapatnam", "Visakhapatnam"}
    );

    private static final List<Object[]> TRAINS = List.of(
            new Object[]{"12951", "Mumbai Rajdhani", "RAJDHANI"},
            new Object[]{"12009", "Ahmedabad Shatabdi", "SHATABDI"},
            new Object[]{"12259", "Sealdah Duronto", "DURONTO"},
            new Object[]{"12302", "Howrah Rajdhani", "RAJDHANI"},
            new Object[]{"12621", "Tamil Nadu Express", "SUPERFAST"},
            new Object[]{"12627", "Karnataka Express", "SUPERFAST"},
            new Object[]{"12909", "Garib Rath Express", "EXPRESS"},
            new Object[]{"12002", "Bhopal Shatabdi", "SHATABDI"},
            new Object[]{"12471", "Swaraj Express", "EXPRESS"},
            new Object[]{"12958", "ADI SC Express", "SUPERFAST"}
    );

    // train number -> ordered stops (station code, cumulative distance km).
    private static final Map<String, List<Stop>> ROUTES = Map.ofEntries(
            Map.entry("12951", route("BCT", 0, "RTM", 528, "KOTA", 862, "NDLS", 1384)),
            Map.entry("12009", route("NDLS", 0, "JP", 309, "ADI", 950, "BCT", 1400)),
            Map.entry("12259", route("HWH", 0, "R", 261, "NGP", 700, "BPL", 1100)),
            Map.entry("12302", route("HWH", 0, "PNBE", 530, "LKO", 990, "NDLS", 1450)),
            Map.entry("12621", route("MAS", 0, "CBE", 495, "SBC", 690, "PUNE", 1200)),
            Map.entry("12627", route("SBC", 0, "SC", 610, "NGP", 1100, "BPL", 1500, "NDLS", 2100)),
            Map.entry("12909", route("BCT", 0, "ADI", 490, "JP", 1150, "NDLS", 1450)),
            Map.entry("12002", route("NDLS", 0, "JP", 309, "KOTA", 570, "BPL", 700)),
            Map.entry("12471", route("ASR", 0, "CDG", 230, "NDLS", 450, "JP", 750)),
            Map.entry("12958", route("ADI", 0, "NGP", 560, "SC", 1000, "MAS", 1400))
    );
}
