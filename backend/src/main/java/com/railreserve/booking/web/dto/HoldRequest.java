package com.railreserve.booking.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldRequest(
        @NotNull Long scheduleId,
        @NotNull Long coachId,
        @NotEmpty List<@NotNull Long> seatIds,
        @NotEmpty @Valid List<PassengerRequest> passengers) {
}
