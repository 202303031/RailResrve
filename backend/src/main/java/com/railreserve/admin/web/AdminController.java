package com.railreserve.admin.web;

import com.railreserve.admin.AdminService;
import com.railreserve.admin.web.dto.CreateScheduleRequest;
import com.railreserve.admin.web.dto.CreateTrainRequest;
import com.railreserve.admin.web.dto.ScheduleResponse;
import com.railreserve.admin.web.dto.TrainResponse;
import com.railreserve.booking.service.BookingQueryService;
import com.railreserve.booking.web.dto.BookingSummary;
import com.railreserve.common.api.ApiResponse;
import com.railreserve.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints. Access is enforced twice: the URL rule ({@code /api/v1/admin/**} needs
 * ADMIN) and this method-level {@code @PreAuthorize} -- defence in depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final BookingQueryService bookingQueryService;

    public AdminController(AdminService adminService, BookingQueryService bookingQueryService) {
        this.adminService = adminService;
        this.bookingQueryService = bookingQueryService;
    }

    @PostMapping("/trains")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TrainResponse> createTrain(@Valid @RequestBody CreateTrainRequest request) {
        return ApiResponse.ok(adminService.createTrain(request));
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.ok(adminService.createSchedule(request));
    }

    @GetMapping("/bookings")
    public ApiResponse<PageResponse<BookingSummary>> allBookings(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(bookingQueryService.listAll(pageable)));
    }
}
