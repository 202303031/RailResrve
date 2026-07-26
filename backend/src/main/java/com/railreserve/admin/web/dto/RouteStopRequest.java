package com.railreserve.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;

public record RouteStopRequest(
        @NotBlank String stationCode,
        LocalTime arrivalTime,
        LocalTime departureTime,
        @PositiveOrZero int distanceKm) {
}
