package com.railreserve.admin.web.dto;

import com.railreserve.scheduling.domain.TravelClass;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CoachRequest(
        @NotBlank String code,
        @NotNull TravelClass travelClass,
        @Min(1) @Max(200) int totalSeats) {
}
