package com.railreserve.catalog.web.dto;

import com.railreserve.catalog.domain.TrainType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** One train running on the requested date between the requested stations. */
public record TrainSearchResult(
        Long scheduleId,
        String trainNumber,
        String trainName,
        TrainType trainType,
        LocalDate journeyDate,
        LocalTime departureTime,
        LocalTime arrivalTime,
        int distanceKm,
        long durationMinutes,
        List<ClassAvailability> availability) {
}
