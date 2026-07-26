package com.railreserve.booking.web.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmRequest(@NotNull Long holdId, String paymentToken) {
}
