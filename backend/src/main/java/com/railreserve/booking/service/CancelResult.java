package com.railreserve.booking.service;

import com.railreserve.booking.web.dto.CancelResponse;

import java.util.List;

/**
 * Outcome of the transactional cancellation step, carrying what the orchestrator needs to run
 * waitlist promotion after the cancel has committed.
 */
public record CancelResult(
        CancelResponse response,
        Long scheduleId,
        Long coachId,
        List<Long> freedSeatIds,
        boolean wasConfirmed) {
}
