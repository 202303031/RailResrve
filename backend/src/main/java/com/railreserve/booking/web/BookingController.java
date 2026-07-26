package com.railreserve.booking.web;

import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.ConfirmRequest;
import com.railreserve.booking.web.dto.ConfirmResponse;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.HoldResponse;
import com.railreserve.common.api.ApiResponse;
import com.railreserve.user.web.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserProvider currentUserProvider;

    public BookingController(BookingService bookingService, CurrentUserProvider currentUserProvider) {
        this.bookingService = bookingService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/hold")
    public ApiResponse<HoldResponse> hold(
            @Valid @RequestBody HoldRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.ok(bookingService.hold(request, userId, idempotencyKey));
    }

    @PostMapping("/confirm")
    public ApiResponse<ConfirmResponse> confirm(@Valid @RequestBody ConfirmRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.ok(bookingService.confirm(request.holdId(), request.paymentToken(), userId));
    }
}
