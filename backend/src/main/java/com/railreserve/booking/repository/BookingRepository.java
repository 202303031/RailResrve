package com.railreserve.booking.repository;

import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnr(String pnr);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByPnr(String pnr);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    /** Waitlisted bookings for a coach, in queue order -- the promotion candidates. */
    List<Booking> findByScheduleIdAndCoachIdAndStatusOrderByWaitlistPositionAsc(
            Long scheduleId, Long coachId, BookingStatus status);

    @Query("SELECT COALESCE(MAX(b.waitlistPosition), 0) FROM Booking b "
            + "WHERE b.schedule.id = :scheduleId AND b.coach.id = :coachId")
    int maxWaitlistPosition(@Param("scheduleId") Long scheduleId, @Param("coachId") Long coachId);
}
