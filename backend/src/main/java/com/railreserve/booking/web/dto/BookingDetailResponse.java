package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BookingDetailResponse(
        String pnr,
        BookingStatus status,
        LocalDate journeyDate,
        String trainNumber,
        String trainName,
        BigDecimal totalFare,
        Integer waitlistPosition,
        Instant createdAt,
        List<PassengerView> passengers) {
}
