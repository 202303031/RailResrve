package com.railreserve.booking.service;

import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingPassenger;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.domain.PassengerStatus;
import com.railreserve.booking.domain.SeatHold;
import com.railreserve.booking.config.BookingProperties;
import com.railreserve.booking.exception.HoldExpiredException;
import com.railreserve.booking.exception.HoldNotFoundException;
import com.railreserve.booking.exception.SeatUnavailableException;
import com.railreserve.booking.lock.PessimisticSeatLockStrategy;
import com.railreserve.booking.lock.SeatLockStrategy;
import com.railreserve.booking.refund.RefundPolicy;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.booking.web.dto.CancelResponse;
import com.railreserve.booking.web.dto.ConfirmResponse;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.HoldResponse;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.common.exception.BusinessRuleException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.payment.domain.Payment;
import com.railreserve.payment.domain.PaymentStatus;
import com.railreserve.payment.repository.PaymentRepository;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.Seat;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.SeatRepository;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Each public method here is a single transactional unit, in a separate bean from
 * {@link BookingService} so the retry loop there re-runs each attempt in a fresh transaction.
 * Isolation is READ_COMMITTED; correctness under concurrency comes from the {@link SeatLockStrategy}
 * on the counter, the per-seat unique indexes, and the booking {@code @Version} -- not a heavier
 * isolation level.
 */
@Service
public class BookingCommandExecutor {

    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final AppUserRepository appUserRepository;
    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final PaymentRepository paymentRepository;
    private final FareCalculator fareCalculator;
    private final PnrGenerator pnrGenerator;
    private final RefundPolicy refundPolicy;
    private final BookingProperties properties;
    // Reliable (row-locking) reserve/release used by the compensation paths (expiry, cancel,
    // promotion), where we must not fail on optimistic conflicts.
    private final PessimisticSeatLockStrategy pessimisticLock;

    public BookingCommandExecutor(CoachRepository coachRepository,
                                  SeatRepository seatRepository,
                                  AppUserRepository appUserRepository,
                                  BookingRepository bookingRepository,
                                  SeatHoldRepository seatHoldRepository,
                                  PaymentRepository paymentRepository,
                                  FareCalculator fareCalculator,
                                  PnrGenerator pnrGenerator,
                                  RefundPolicy refundPolicy,
                                  BookingProperties properties,
                                  PessimisticSeatLockStrategy pessimisticLock) {
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.appUserRepository = appUserRepository;
        this.bookingRepository = bookingRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.paymentRepository = paymentRepository;
        this.fareCalculator = fareCalculator;
        this.pnrGenerator = pnrGenerator;
        this.refundPolicy = refundPolicy;
        this.properties = properties;
        this.pessimisticLock = pessimisticLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldResponse holdOnce(HoldRequest request, Long userId, String idempotencyKey, SeatLockStrategy strategy) {
        // 1. Idempotency replay: a retried request with the same key returns the same hold.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toHoldResponse(existing.get());
            }
        }

        // 2. Validate the request and load its entities.
        List<Long> seatIds = request.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Duplicate seat id in request");
        }
        if (seatIds.size() != request.passengers().size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Seat count must match passenger count");
        }
        Coach coach = coachRepository.findById(request.coachId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Coach " + request.coachId() + " not found"));
        if (!coach.getSchedule().getId().equals(request.scheduleId())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Coach does not belong to the schedule");
        }
        Schedule schedule = coach.getSchedule();
        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "One or more seats not found");
        }
        for (Seat seat : seats) {
            if (!seat.getCoach().getId().equals(coach.getId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                        "Seat " + seat.getId() + " is not in coach " + coach.getId());
            }
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        // 3. Reserve the aggregate counter (strategy). May throw SeatUnavailable, or for the
        //    optimistic strategy OptimisticLockingFailureException (caller retries).
        strategy.reserve(schedule.getId(), coach.getId(), seats.size());

        // 4. Create the HELD booking (its id IS the hold group), passengers, and seat holds.
        Map<Long, Seat> seatById = seats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
        BigDecimal totalFare = fareCalculator.totalFare(coach.getTravelClass(), seats.size());
        Instant expiresAt = Instant.now().plusSeconds(properties.holdTtlSeconds());
        Booking booking = new Booking(uniquePnr(), user, schedule, coach, BookingStatus.HELD, totalFare, idempotencyKey);
        for (int i = 0; i < seatIds.size(); i++) {
            Seat seat = seatById.get(seatIds.get(i));
            PassengerRequest passenger = request.passengers().get(i);
            booking.addPassenger(new BookingPassenger(seat, passenger.name(), passenger.age(),
                    passenger.gender(), PassengerStatus.CONFIRMED));
        }
        bookingRepository.save(booking);
        for (Long seatId : seatIds) {
            SeatHold hold = new SeatHold(seatById.get(seatId), schedule, user, expiresAt);
            hold.setBooking(booking);
            seatHoldRepository.save(hold);
        }

        // 5. Flush so the per-seat unique indexes (and the idempotency index) are checked here.
        try {
            bookingRepository.flush();
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("uq_seat_hold_live") || cause.contains("uq_passenger_seat_active")) {
                throw new SeatUnavailableException("One or more selected seats were just taken");
            }
            if (cause.contains("uq_booking_idempotency")) {
                throw new ConcurrentDuplicateRequestException();
            }
            throw e;
        }
        return new HoldResponse(booking.getId(), expiresAt, totalFare);
    }

    /**
     * Step 1 of the confirm saga (its own short transaction): validate ownership and that the hold
     * is still live, and snapshot what the charge needs (PNR + fare). Returns
     * {@link PreparedConfirm#alreadyConfirmed} for an idempotent replay so the caller skips charging.
     * The remote charge happens <b>after</b> this transaction commits, so no DB connection is held
     * open across the network call.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PreparedConfirm prepareConfirm(Long holdId, Long userId) {
        Booking booking = loadOwnedHold(holdId, userId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return PreparedConfirm.alreadyConfirmed(new ConfirmResponse(booking.getPnr(), booking.getStatus()));
        }
        assertHoldIsLive(booking);
        return PreparedConfirm.pending(booking.getPnr(), booking.getTotalFare());
    }

    /**
     * Step 3 of the confirm saga (its own short transaction, run only after an APPROVED charge):
     * flip the booking and its holds to CONFIRMED and record the successful payment atomically.
     * Idempotent, and re-checks the hold because it may have expired between prepare and here; the
     * booking {@code @Version} guards against a racing expiry (an optimistic conflict makes the
     * caller retry). Any failure here means we charged but could not confirm — the caller
     * compensates with a refund.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConfirmResponse finalizeConfirm(Long holdId, Long userId, String gatewayRef) {
        Booking booking = loadOwnedHold(holdId, userId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return new ConfirmResponse(booking.getPnr(), booking.getStatus());
        }
        List<SeatHold> holds = assertHoldIsLive(booking);
        booking.transitionTo(BookingStatus.CONFIRMED);
        holds.forEach(hold -> hold.setStatus(HoldStatus.CONFIRMED));
        paymentRepository.save(new Payment(booking, booking.getTotalFare(), PaymentStatus.SUCCESS, gatewayRef));
        bookingRepository.saveAndFlush(booking); // booking @Version guards against a racing expiry
        return new ConfirmResponse(booking.getPnr(), booking.getStatus());
    }

    /** Record an audit trail row for a declined charge (booking stays HELD, its own transaction). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordDeclinedPayment(Long holdId) {
        bookingRepository.findById(holdId).ifPresent(booking ->
                paymentRepository.save(new Payment(booking, booking.getTotalFare(), PaymentStatus.FAILED, null)));
    }

    /** Record the compensating refund after a charge that could not be confirmed (its own tx). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordCompensatingRefund(Long holdId, String gatewayRef, BigDecimal amount) {
        bookingRepository.findById(holdId).ifPresent(booking ->
                paymentRepository.save(new Payment(booking, amount, PaymentStatus.REFUNDED, gatewayRef)));
    }

    private Booking loadOwnedHold(Long holdId, Long userId) {
        Booking booking = bookingRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException("No hold with id " + holdId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new HoldNotFoundException("No hold with id " + holdId);
        }
        return booking;
    }

    private List<SeatHold> assertHoldIsLive(Booking booking) {
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new HoldExpiredException("Hold is no longer active (status " + booking.getStatus() + ")");
        }
        List<SeatHold> holds = seatHoldRepository.findByBookingId(booking.getId());
        Instant now = Instant.now();
        boolean invalid = holds.isEmpty() || holds.stream()
                .anyMatch(hold -> hold.getStatus() != HoldStatus.ACTIVE || hold.getExpiresAt().isBefore(now));
        if (invalid) {
            throw new HoldExpiredException("Hold has expired");
        }
        return holds;
    }

    @Transactional(readOnly = true)
    public List<Long> findBookingIdsWithExpiredHolds(Instant cutoff) {
        return seatHoldRepository.findBookingIdsWithExpiredHolds(HoldStatus.ACTIVE, cutoff);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expireOneBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.HELD) {
            return false;
        }
        List<SeatHold> activeHolds = seatHoldRepository.findByBookingId(bookingId).stream()
                .filter(hold -> hold.getStatus() == HoldStatus.ACTIVE)
                .toList();
        if (activeHolds.isEmpty()) {
            return false;
        }
        Long coachId = activeHolds.get(0).getSeat().getCoach().getId();
        pessimisticLock.release(booking.getSchedule().getId(), coachId, activeHolds.size());
        activeHolds.forEach(hold -> hold.setStatus(HoldStatus.EXPIRED));
        booking.getPassengers().forEach(passenger -> passenger.setStatus(PassengerStatus.CANCELLED));
        booking.transitionTo(BookingStatus.EXPIRED);
        bookingRepository.saveAndFlush(booking);
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelResult cancelOnce(String pnr, Long userId) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + pnr + " not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + pnr + " not found");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return new CancelResult(new CancelResponse(pnr, BookingStatus.CANCELLED, BigDecimal.ZERO),
                    null, null, List.of(), false);
        }

        boolean wasConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;

        List<SeatHold> liveHolds = seatHoldRepository.findByBookingId(booking.getId()).stream()
                .filter(hold -> hold.getStatus() == HoldStatus.ACTIVE || hold.getStatus() == HoldStatus.CONFIRMED)
                .toList();
        List<Long> freedSeatIds = new ArrayList<>();
        Long coachId = booking.getCoach() != null ? booking.getCoach().getId() : null;
        if (!liveHolds.isEmpty()) {
            if (coachId == null) {
                coachId = liveHolds.get(0).getSeat().getCoach().getId();
            }
            pessimisticLock.release(booking.getSchedule().getId(), coachId, liveHolds.size());
            liveHolds.forEach(hold -> {
                freedSeatIds.add(hold.getSeat().getId());
                hold.setStatus(HoldStatus.RELEASED);
            });
        }
        booking.getPassengers().forEach(passenger -> passenger.setStatus(PassengerStatus.CANCELLED));

        BigDecimal refund = refundPolicy.computeRefund(booking.getTotalFare(), timeUntilDeparture(booking));
        if (refund.signum() > 0) {
            paymentRepository.save(new Payment(booking, refund, PaymentStatus.REFUNDED, null));
        }

        booking.transitionTo(BookingStatus.CANCELLED);
        bookingRepository.saveAndFlush(booking);
        return new CancelResult(new CancelResponse(pnr, BookingStatus.CANCELLED, refund),
                booking.getSchedule().getId(), coachId, freedSeatIds, wasConfirmed);
    }

    @Transactional(readOnly = true)
    public List<Long> waitlistCandidates(Long scheduleId, Long coachId) {
        return bookingRepository
                .findByScheduleIdAndCoachIdAndStatusOrderByWaitlistPositionAsc(scheduleId, coachId, BookingStatus.WAITLISTED)
                .stream().map(Booking::getId).toList();
    }

    /**
     * Promote one waitlisted booking onto {@code freedSeatIds}. Returns how many seats it used
     * (0 if it could not be promoted). The booking {@code @Version} makes a concurrent promotion
     * of the same booking fail; a freed seat grabbed by someone else fails on the unique index --
     * either way this rolls back and the caller moves on.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int promoteOne(Long waitlistBookingId, Long scheduleId, Long coachId, List<Long> freedSeatIds) {
        Booking waitlisted = bookingRepository.findById(waitlistBookingId).orElse(null);
        if (waitlisted == null || waitlisted.getStatus() != BookingStatus.WAITLISTED) {
            return 0;
        }
        int need = waitlisted.getPassengers().size();
        if (need > freedSeatIds.size()) {
            return 0;
        }

        pessimisticLock.reserve(scheduleId, coachId, need);
        List<Long> take = freedSeatIds.subList(0, need);
        Map<Long, Seat> seatById = seatRepository.findAllById(take).stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));

        Instant confirmedHoldExpiry = Instant.now().plus(Duration.ofDays(365));
        List<BookingPassenger> passengers = waitlisted.getPassengers();
        for (int i = 0; i < need; i++) {
            Seat seat = seatById.get(take.get(i));
            BookingPassenger passenger = passengers.get(i);
            passenger.assignSeat(seat);
            passenger.setStatus(PassengerStatus.CONFIRMED);
            SeatHold hold = new SeatHold(seat, waitlisted.getSchedule(), waitlisted.getUser(), confirmedHoldExpiry);
            hold.setStatus(HoldStatus.CONFIRMED);
            hold.setBooking(waitlisted);
            seatHoldRepository.save(hold);
        }
        waitlisted.setWaitlistPosition(null);
        waitlisted.transitionTo(BookingStatus.CONFIRMED);
        try {
            bookingRepository.saveAndFlush(waitlisted);
        } catch (DataIntegrityViolationException e) {
            // A freed seat was taken by someone else in the gap; roll back and let the next
            // cancellation try again.
            throw new SeatUnavailableException("A freed seat was taken before promotion completed");
        }
        return need;
    }

    private Duration timeUntilDeparture(Booking booking) {
        Instant departure = booking.getSchedule().getJourneyDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return Duration.between(Instant.now(), departure);
    }

    private HoldResponse toHoldResponse(Booking booking) {
        Instant expiresAt = seatHoldRepository.findByBookingId(booking.getId()).stream()
                .map(SeatHold::getExpiresAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new HoldResponse(booking.getId(), expiresAt, booking.getTotalFare());
    }

    private String uniquePnr() {
        for (int i = 0; i < 5; i++) {
            String pnr = pnrGenerator.generate();
            if (!bookingRepository.existsByPnr(pnr)) {
                return pnr;
            }
        }
        throw new IllegalStateException("Unable to generate a unique PNR");
    }
}
