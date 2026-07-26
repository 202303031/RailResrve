package com.railreserve.admin.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateScheduleRequest(
        @NotNull Long trainId,
        @NotNull @FutureOrPresent LocalDate journeyDate,
        @NotEmpty @Valid List<CoachRequest> coaches) {
}
