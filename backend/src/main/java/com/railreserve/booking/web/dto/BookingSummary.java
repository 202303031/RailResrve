package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingSummary(
        String pnr,
        BookingStatus status,
        LocalDate journeyDate,
        String trainNumber,
        String trainName,
        BigDecimal totalFare,
        int passengerCount,
        Instant createdAt) {
}
