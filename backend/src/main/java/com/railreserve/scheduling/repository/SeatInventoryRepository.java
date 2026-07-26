package com.railreserve.scheduling.repository;

import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.web.dto.CoachAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, Long> {

    List<SeatInventory> findByScheduleId(Long scheduleId);

    Optional<SeatInventory> findByScheduleIdAndCoachId(Long scheduleId, Long coachId);

    /**
     * Loads the inventory row under a {@code SELECT ... FOR UPDATE} lock (pessimistic
     * strategy). Concurrent reservers of the same coach serialize on this row lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SeatInventory i WHERE i.schedule.id = :scheduleId AND i.coach.id = :coachId")
    Optional<SeatInventory> findForUpdate(@Param("scheduleId") Long scheduleId, @Param("coachId") Long coachId);

    @Query("""
            SELECT new com.railreserve.scheduling.web.dto.CoachAvailability(
                       c.id, c.coachCode, c.travelClass, i.availableCount, c.totalSeats)
            FROM SeatInventory i
                JOIN i.coach c
            WHERE i.schedule.id = :scheduleId
            ORDER BY c.coachCode
            """)
    List<CoachAvailability> findCoachAvailability(@Param("scheduleId") Long scheduleId);
}
