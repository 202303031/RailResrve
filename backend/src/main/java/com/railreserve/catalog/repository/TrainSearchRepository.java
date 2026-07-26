package com.railreserve.catalog.repository;

import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only queries backing train search. A train serves the route only if its origin stop
 * comes before its destination stop (stop_order), which is expressed by joining route_stop
 * twice and requiring {@code dep.stopOrder < arr.stopOrder}.
 */
public interface TrainSearchRepository extends Repository<Schedule, Long> {

    @Query(value = """
            SELECT new com.railreserve.catalog.repository.SearchScheduleRow(
                       s.id, t.number, t.name, t.type,
                       dep.departureTime, arr.arrivalTime,
                       arr.distanceKm - dep.distanceKm, s.journeyDate)
            FROM Schedule s
                JOIN s.train t
                JOIN RouteStop dep ON dep.train = t
                JOIN dep.station depSt
                JOIN RouteStop arr ON arr.train = t
                JOIN arr.station arrSt
            WHERE s.journeyDate = :date
              AND s.status = :status
              AND depSt.code = :fromCode
              AND arrSt.code = :toCode
              AND dep.stopOrder < arr.stopOrder
            ORDER BY dep.departureTime
            """,
            countQuery = """
            SELECT count(s)
            FROM Schedule s
                JOIN s.train t
                JOIN RouteStop dep ON dep.train = t
                JOIN dep.station depSt
                JOIN RouteStop arr ON arr.train = t
                JOIN arr.station arrSt
            WHERE s.journeyDate = :date
              AND s.status = :status
              AND depSt.code = :fromCode
              AND arrSt.code = :toCode
              AND dep.stopOrder < arr.stopOrder
            """)
    Page<SearchScheduleRow> search(@Param("fromCode") String fromCode,
                                   @Param("toCode") String toCode,
                                   @Param("date") LocalDate date,
                                   @Param("status") ScheduleStatus status,
                                   Pageable pageable);

    @Query("""
            SELECT new com.railreserve.catalog.repository.ScheduleClassAvailability(
                       i.schedule.id, c.travelClass, SUM(i.availableCount))
            FROM SeatInventory i
                JOIN i.coach c
            WHERE i.schedule.id IN :scheduleIds
            GROUP BY i.schedule.id, c.travelClass
            """)
    List<ScheduleClassAvailability> classAvailability(@Param("scheduleIds") List<Long> scheduleIds);
}
