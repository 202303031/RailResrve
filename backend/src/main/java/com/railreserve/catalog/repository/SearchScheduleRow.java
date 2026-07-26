package com.railreserve.catalog.repository;

import com.railreserve.catalog.domain.TrainType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Flat internal projection produced by the search query (one row per matching schedule).
 * The service assembles these plus per-class availability into the API-facing result.
 */
public record SearchScheduleRow(
        Long scheduleId,
        String trainNumber,
        String trainName,
        TrainType trainType,
        LocalTime departureTime,
        LocalTime arrivalTime,
        Integer distanceKm,
        LocalDate journeyDate) {
}
