package com.railreserve.scheduling.web.dto;

import com.railreserve.scheduling.domain.TravelClass;

import java.util.List;

/** The seat grid for one coach on a schedule, used by the seat-selection page. */
public record SeatMapResponse(
        Long scheduleId,
        Long coachId,
        String coachCode,
        TravelClass travelClass,
        List<SeatView> seats) {
}
