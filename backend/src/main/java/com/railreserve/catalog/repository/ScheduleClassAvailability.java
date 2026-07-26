package com.railreserve.catalog.repository;

import com.railreserve.scheduling.domain.TravelClass;

/** Internal projection: total available seats per (schedule, travel class). */
public record ScheduleClassAvailability(Long scheduleId, TravelClass travelClass, Long availableSeats) {
}
