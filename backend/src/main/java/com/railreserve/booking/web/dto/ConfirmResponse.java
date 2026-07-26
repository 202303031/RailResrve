package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.BookingStatus;

public record ConfirmResponse(String pnr, BookingStatus status) {
}
