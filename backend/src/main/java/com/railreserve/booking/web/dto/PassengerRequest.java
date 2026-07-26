package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PassengerRequest(
        @NotBlank String name,
        @Min(1) @Max(119) int age,
        @NotNull Gender gender) {
}
