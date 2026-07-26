package com.railreserve.booking.lock;

/**
 * Strategy for atomically adjusting a coach's aggregate seat counter under concurrency.
 * Two implementations exist -- optimistic ({@code @Version}) and pessimistic
 * ({@code SELECT ... FOR UPDATE}) -- and are interchangeable behind this interface.
 *
 * <p>Implementations run inside the caller's transaction. The optimistic one may throw
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}; the booking
 * service retries the whole operation when that happens.
 */
public interface SeatLockStrategy {

    /** Lookup name of this strategy: {@code "optimistic"} or {@code "pessimistic"}. */
    String name();

    /**
     * Reserve {@code count} seats from the (schedule, coach) inventory.
     *
     * @throws com.railreserve.booking.exception.SeatUnavailableException if fewer than
     *         {@code count} seats remain
     */
    void reserve(Long scheduleId, Long coachId, int count);

    /** Return {@code count} previously-reserved seats to the inventory. */
    void release(Long scheduleId, Long coachId, int count);
}
