package com.railreserve.scheduling.web.dto;

import com.railreserve.scheduling.domain.TravelClass;

/** Availability for a single coach on a schedule. Built directly by a JPQL projection. */
public record CoachAvailability(
        Long coachId,
        String coachCode,
        TravelClass travelClass,
        Integer availableCount,
        Integer totalSeats) {
}
