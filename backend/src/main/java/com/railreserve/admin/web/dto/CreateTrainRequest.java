package com.railreserve.admin.web.dto;

import com.railreserve.catalog.domain.TrainType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTrainRequest(
        @NotBlank @Size(max = 10) String number,
        @NotBlank @Size(max = 100) String name,
        @NotNull TrainType type,
        @NotEmpty @Valid List<RouteStopRequest> stops) {
}
