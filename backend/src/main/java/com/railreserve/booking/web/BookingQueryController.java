package com.railreserve.booking.web;

import com.railreserve.booking.service.BookingQueryService;
import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.BookingDetailResponse;
import com.railreserve.booking.web.dto.BookingSummary;
import com.railreserve.booking.web.dto.CancelResponse;
import com.railreserve.common.api.ApiResponse;
import com.railreserve.common.api.PageResponse;
import com.railreserve.user.web.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingQueryController {

    private final BookingQueryService bookingQueryService;
    private final BookingService bookingService;
    private final CurrentUserProvider currentUserProvider;

    public BookingQueryController(BookingQueryService bookingQueryService,
                                  BookingService bookingService,
                                  CurrentUserProvider currentUserProvider) {
        this.bookingQueryService = bookingQueryService;
        this.bookingService = bookingService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<PageResponse<BookingSummary>> myBookings(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.ok(PageResponse.from(bookingQueryService.listForUser(userId, pageable)));
    }

    @GetMapping("/{pnr}")
    public ApiResponse<BookingDetailResponse> byPnr(@PathVariable String pnr) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.ok(bookingQueryService.getForUser(pnr, userId));
    }

    @DeleteMapping("/{pnr}")
    public ApiResponse<CancelResponse> cancel(@PathVariable String pnr) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.ok(bookingService.cancel(pnr, userId));
    }
}
