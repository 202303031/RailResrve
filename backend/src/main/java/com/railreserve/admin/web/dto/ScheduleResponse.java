package com.railreserve.admin.web.dto;

import java.time.LocalDate;

public record ScheduleResponse(Long id, String trainNumber, LocalDate journeyDate, int coachCount, int totalSeats) {
}
