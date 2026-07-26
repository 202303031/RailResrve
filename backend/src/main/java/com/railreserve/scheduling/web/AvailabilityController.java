package com.railreserve.scheduling.web;

import com.railreserve.common.api.ApiResponse;
import com.railreserve.scheduling.domain.TravelClass;
import com.railreserve.scheduling.service.AvailabilityService;
import com.railreserve.scheduling.web.dto.AvailabilityResponse;
import com.railreserve.scheduling.web.dto.SeatMapResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{id}/availability")
    public ApiResponse<AvailabilityResponse> availability(
            @PathVariable Long id,
            @RequestParam(required = false) TravelClass travelClass) {
        return ApiResponse.ok(availabilityService.getAvailability(id, travelClass));
    }

    @GetMapping("/{id}/coaches/{coachId}/seats")
    public ApiResponse<SeatMapResponse> seatMap(@PathVariable Long id, @PathVariable Long coachId) {
        return ApiResponse.ok(availabilityService.getSeatMap(id, coachId));
    }
}
