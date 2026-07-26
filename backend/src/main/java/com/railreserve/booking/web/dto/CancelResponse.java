package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.BookingStatus;

import java.math.BigDecimal;

public record CancelResponse(String pnr, BookingStatus status, BigDecimal refundAmount) {
}
