package com.railreserve.booking.domain;

import com.railreserve.booking.exception.InvalidBookingStateException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for legal booking transitions. {@link Booking#transitionTo} calls
 * {@link #assertCanTransition}, so an illegal transition is impossible to perform in code -- it
 * throws rather than silently corrupting state.
 *
 * <pre>
 *   PENDING -> HELD | CANCELLED | EXPIRED
 *   HELD    -> CONFIRMED | WAITLISTED | RAC | CANCELLED | EXPIRED
 *   WAITLISTED / RAC -> CONFIRMED | (RAC) | CANCELLED | EXPIRED
 *   CONFIRMED -> CANCELLED
 *   CANCELLED / EXPIRED -> (terminal)
 * </pre>
 */
public final class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        TRANSITIONS.put(BookingStatus.PENDING,
                EnumSet.of(BookingStatus.HELD, BookingStatus.CANCELLED, BookingStatus.EXPIRED));
        TRANSITIONS.put(BookingStatus.HELD,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED, BookingStatus.RAC,
                        BookingStatus.CANCELLED, BookingStatus.EXPIRED));
        TRANSITIONS.put(BookingStatus.WAITLISTED,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.RAC, BookingStatus.CANCELLED, BookingStatus.EXPIRED));
        TRANSITIONS.put(BookingStatus.RAC,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED));
        TRANSITIONS.put(BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        TRANSITIONS.put(BookingStatus.EXPIRED, EnumSet.noneOf(BookingStatus.class));
    }

    private BookingStateMachine() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertCanTransition(BookingStatus from, BookingStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidBookingStateException("Illegal booking transition: " + from + " -> " + to);
        }
    }
}
