package com.railreserve.booking.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record HoldResponse(Long holdId, Instant expiresAt, BigDecimal totalFare) {
}
