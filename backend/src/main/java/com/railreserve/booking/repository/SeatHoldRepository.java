package com.railreserve.booking.repository;

import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.domain.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    List<SeatHold> findByBookingId(Long bookingId);

    /** Active holds whose TTL has elapsed -- the input to the expiry sweep. */
    List<SeatHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant cutoff);

    /** Distinct bookings that have at least one active hold past its TTL. */
    @Query("SELECT DISTINCT h.booking.id FROM SeatHold h WHERE h.status = :status AND h.expiresAt < :cutoff")
    List<Long> findBookingIdsWithExpiredHolds(@Param("status") HoldStatus status, @Param("cutoff") Instant cutoff);

    /** Seat ids on a schedule that currently have a live claim (ACTIVE hold or CONFIRMED booking). */
    @Query("SELECT h.seat.id FROM SeatHold h WHERE h.schedule.id = :scheduleId AND h.status IN :statuses")
    List<Long> findLiveHeldSeatIds(@Param("scheduleId") Long scheduleId, @Param("statuses") List<HoldStatus> statuses);
}
