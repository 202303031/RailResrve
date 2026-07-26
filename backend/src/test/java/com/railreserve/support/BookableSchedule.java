package com.railreserve.support;

import java.util.List;

/** Handle to a ready-to-book schedule created by {@link TestDataFactory}. */
public record BookableSchedule(Long scheduleId, Long coachId, List<Long> seatIds, Long userId) {
}
