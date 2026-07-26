package com.railreserve.booking.service;

import com.railreserve.booking.config.BookingProperties;
import com.railreserve.booking.exception.SeatUnavailableException;
import com.railreserve.booking.lock.SeatLockStrategy;
import com.railreserve.booking.web.dto.CancelResponse;
import com.railreserve.booking.web.dto.ConfirmResponse;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.HoldResponse;
import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.BusinessRuleException;
import com.railreserve.common.exception.ConflictException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.observability.BookingMetrics;
import com.railreserve.payment.PaymentProperties;
import com.railreserve.payment.gateway.ChargeRequest;
import com.railreserve.payment.gateway.ChargeResult;
import com.railreserve.payment.gateway.PaymentGateway;
import com.railreserve.payment.gateway.PaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public façade for booking. It owns the <b>bounded retry loop</b> that gives the optimistic
 * strategy its resilience: on a version conflict the whole hold/confirm is re-run in a fresh
 * transaction (via {@link BookingCommandExecutor}, a separate proxied bean). The pessimistic
 * strategy never triggers a retry because it serializes on a row lock instead.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingCommandExecutor executor;
    private final Map<String, SeatLockStrategy> strategies;
    private final BookingProperties properties;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties paymentProperties;
    private final BookingMetrics metrics;

    public BookingService(BookingCommandExecutor executor,
                          List<SeatLockStrategy> strategyBeans,
                          BookingProperties properties,
                          PaymentGateway paymentGateway,
                          PaymentProperties paymentProperties,
                          BookingMetrics metrics) {
        this.executor = executor;
        this.strategies = strategyBeans.stream()
                .collect(Collectors.toMap(SeatLockStrategy::name, Function.identity()));
        this.properties = properties;
        this.paymentGateway = paymentGateway;
        this.paymentProperties = paymentProperties;
        this.metrics = metrics;
    }

    public HoldResponse hold(HoldRequest request, Long userId, String idempotencyKey) {
        return hold(request, userId, idempotencyKey, properties.lockStrategy());
    }

    /** Package-private overload so tests can exercise a specific locking strategy by name. */
    HoldResponse hold(HoldRequest request, Long userId, String idempotencyKey, String strategyName) {
        SeatLockStrategy strategy = resolve(strategyName);
        int maxRetries = properties.maxLockRetries();
        for (int attempt = 0; ; attempt++) {
            try {
                HoldResponse response = executor.holdOnce(request, userId, idempotencyKey, strategy);
                metrics.holdCreated();
                return response;
            } catch (OptimisticLockingFailureException | ConcurrentDuplicateRequestException e) {
                metrics.lockRetry();
                if (attempt >= maxRetries) {
                    throw new ConflictException(ErrorCode.SEAT_UNAVAILABLE,
                            "Seat contention is too high right now, please try again");
                }
                backoff(attempt);
            }
        }
    }

    /**
     * Confirm a held booking. This is a <b>saga</b>: confirmation spans our database and an external
     * payment provider, which cannot be one atomic transaction, so it is a sequence of steps each
     * with a compensating action.
     * <ol>
     *   <li><b>Prepare</b> (DB tx): validate the hold, snapshot PNR + fare. An already-confirmed
     *       booking short-circuits here — an idempotent replay that never charges twice.</li>
     *   <li><b>Charge</b> (remote, no DB tx held open): only an APPROVED charge proceeds. A decline
     *       is recorded and surfaced as a business error; a technical failure (timeout/5xx) is
     *       treated as indeterminate and also stops confirmation.</li>
     *   <li><b>Finalize</b> (DB tx): flip to CONFIRMED and record the payment atomically, retried on
     *       an optimistic conflict with a racing expiry.</li>
     *   <li><b>Compensate</b>: if finalize ultimately fails after the charge succeeded, refund the
     *       charge so we never keep money for an unconfirmed booking.</li>
     * </ol>
     * The PNR is the charge idempotency key, so even a retried whole-confirm cannot double-charge.
     */
    public ConfirmResponse confirm(Long holdId, String paymentToken, Long userId) {
        PreparedConfirm prepared = executor.prepareConfirm(holdId, userId);
        if (prepared.alreadyConfirmed()) {
            return prepared.response();
        }

        ChargeResult charge = chargeOrThrow(holdId, prepared, paymentToken);

        try {
            ConfirmResponse response = finalizeWithRetry(holdId, userId, charge.gatewayRef());
            metrics.bookingConfirmed();
            return response;
        } catch (RuntimeException confirmFailure) {
            compensate(holdId, charge.gatewayRef(), prepared.totalFare(), confirmFailure);
            throw asConfirmFailure(confirmFailure);
        }
    }

    private ChargeResult chargeOrThrow(Long holdId, PreparedConfirm prepared, String paymentToken) {
        ChargeResult charge;
        try {
            charge = paymentGateway.charge(new ChargeRequest(
                    prepared.pnr(), prepared.totalFare(), paymentProperties.currency(), paymentToken));
        } catch (PaymentGatewayException e) {
            // Indeterminate: money may or may not have moved; do not confirm.
            metrics.paymentFailed();
            log.warn("Payment gateway failure confirming {}: {}", prepared.pnr(), e.getMessage());
            throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED,
                    "Payment could not be processed right now, please try again");
        }
        if (!charge.isApproved()) {
            metrics.paymentFailed();
            executor.recordDeclinedPayment(holdId);
            throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "Payment was declined");
        }
        return charge;
    }

    private ConfirmResponse finalizeWithRetry(Long holdId, Long userId, String gatewayRef) {
        int maxRetries = properties.maxLockRetries();
        for (int attempt = 0; ; attempt++) {
            try {
                return executor.finalizeConfirm(holdId, userId, gatewayRef);
            } catch (OptimisticLockingFailureException e) {
                metrics.lockRetry();
                if (attempt >= maxRetries) {
                    throw e; // give up — the outer saga compensates with a refund
                }
                backoff(attempt);
            }
        }
    }

    private void compensate(Long holdId, String gatewayRef, java.math.BigDecimal amount, RuntimeException cause) {
        log.warn("Charged {} for hold {} but confirmation failed ({}); issuing compensating refund",
                gatewayRef, holdId, cause.toString());
        try {
            paymentGateway.refund(gatewayRef);
        } catch (RuntimeException refundFailure) {
            log.error("Compensating refund of {} failed; needs manual reconciliation", gatewayRef, refundFailure);
        }
        try {
            executor.recordCompensatingRefund(holdId, gatewayRef, amount);
        } catch (RuntimeException recordFailure) {
            log.error("Could not record compensating refund of {} for hold {}", gatewayRef, holdId, recordFailure);
        }
    }

    private RuntimeException asConfirmFailure(RuntimeException confirmFailure) {
        // Business errors (e.g. the hold expired mid-saga) keep their own code and HTTP status; the
        // charge has been refunded by compensate(). Anything else becomes a payment failure.
        if (confirmFailure instanceof ApiException apiException) {
            return apiException;
        }
        return new BusinessRuleException(ErrorCode.ILLEGAL_BOOKING_STATE,
                "Could not confirm due to a concurrent change; your payment was refunded, please retry");
    }

    public int expireStaleHolds() {
        List<Long> bookingIds = executor.findBookingIdsWithExpiredHolds(Instant.now());
        int expired = 0;
        for (Long bookingId : bookingIds) {
            try {
                if (executor.expireOneBooking(bookingId)) {
                    expired++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Skipped expiry of booking {} (modified concurrently)", bookingId);
            }
        }
        metrics.holdsExpired(expired);
        return expired;
    }

    /**
     * Cancels a booking (refund computed by the refund policy) and, if a confirmed booking freed
     * seats, promotes waitlisted bookings onto them -- each promotion in its own transaction so a
     * clash on one doesn't undo the cancel or the others.
     */
    public CancelResponse cancel(String pnr, Long userId) {
        CancelResult result = executor.cancelOnce(pnr, userId);
        if (result.wasConfirmed() && result.coachId() != null && !result.freedSeatIds().isEmpty()) {
            promoteWaitlist(result.scheduleId(), result.coachId(), result.freedSeatIds());
        }
        return result.response();
    }

    private void promoteWaitlist(Long scheduleId, Long coachId, List<Long> freedSeatIds) {
        List<Long> available = new ArrayList<>(freedSeatIds);
        for (Long waitlistBookingId : executor.waitlistCandidates(scheduleId, coachId)) {
            if (available.isEmpty()) {
                break;
            }
            try {
                int used = executor.promoteOne(waitlistBookingId, scheduleId, coachId, List.copyOf(available));
                if (used > 0) {
                    available.subList(0, used).clear();
                }
            } catch (OptimisticLockingFailureException | SeatUnavailableException e) {
                log.debug("Skipped promotion of waitlisted booking {} ({})", waitlistBookingId, e.getMessage());
            }
        }
    }

    private SeatLockStrategy resolve(String name) {
        SeatLockStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown lock strategy: " + name
                    + " (known: " + strategies.keySet() + ")");
        }
        return strategy;
    }

    private void backoff(int attempt) {
        try {
            long millis = Math.min(50L, 2L * (attempt + 1)) + ThreadLocalRandom.current().nextLong(5);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", e);
        }
    }
}
