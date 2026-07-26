package com.railreserve.scheduling.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Per-coach seat availability for one schedule, optionally filtered to a travel class. */
public record AvailabilityResponse(
        Long scheduleId,
        LocalDate journeyDate,
        String trainNumber,
        String trainName,
        List<CoachAvailability> coaches) {
}
