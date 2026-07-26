package com.railreserve.booking.service;

import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.web.dto.BookingDetailResponse;
import com.railreserve.booking.web.dto.BookingSummary;
import com.railreserve.booking.web.dto.PassengerView;
import com.railreserve.catalog.domain.Train;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.scheduling.domain.Seat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingQueryService {

    private final BookingRepository bookingRepository;

    public BookingQueryService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public Page<BookingSummary> listForUser(Long userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable).map(this::toSummary);
    }

    /** All bookings, for the admin view. */
    @Transactional(readOnly = true)
    public Page<BookingSummary> listAll(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse getForUser(String pnr, Long userId) {
        Booking booking = requireOwnedBooking(pnr, userId);
        return toDetail(booking);
    }

    /**
     * Loads a booking and checks ownership. To a non-owner the booking looks absent (404 not
     * 403), so its existence isn't leaked. Phase 6 relaxes this for ADMIN.
     */
    Booking requireOwnedBooking(String pnr, Long userId) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + pnr + " not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + pnr + " not found");
        }
        return booking;
    }

    private BookingSummary toSummary(Booking booking) {
        Train train = booking.getSchedule().getTrain();
        return new BookingSummary(booking.getPnr(), booking.getStatus(), booking.getSchedule().getJourneyDate(),
                train.getNumber(), train.getName(), booking.getTotalFare(),
                booking.getPassengers().size(), booking.getCreatedAt());
    }

    private BookingDetailResponse toDetail(Booking booking) {
        Train train = booking.getSchedule().getTrain();
        List<PassengerView> passengers = booking.getPassengers().stream()
                .map(this::toPassengerView)
                .toList();
        return new BookingDetailResponse(booking.getPnr(), booking.getStatus(), booking.getSchedule().getJourneyDate(),
                train.getNumber(), train.getName(), booking.getTotalFare(), booking.getWaitlistPosition(),
                booking.getCreatedAt(), passengers);
    }

    private PassengerView toPassengerView(com.railreserve.booking.domain.BookingPassenger passenger) {
        Seat seat = passenger.getSeat();
        return new PassengerView(passenger.getName(), passenger.getAge(), passenger.getGender(),
                passenger.getStatus(),
                seat == null ? null : seat.getSeatNumber(),
                seat == null ? null : seat.getCoach().getCoachCode());
    }
}
