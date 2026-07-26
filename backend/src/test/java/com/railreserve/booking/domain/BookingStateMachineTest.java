package com.railreserve.booking.domain;

import com.railreserve.booking.exception.InvalidBookingStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingStateMachineTest {

    @Test
    void legalTransitionsAreAllowed() {
        assertThat(BookingStateMachine.canTransition(BookingStatus.HELD, BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStateMachine.canTransition(BookingStatus.HELD, BookingStatus.EXPIRED)).isTrue();
        assertThat(BookingStateMachine.canTransition(BookingStatus.CONFIRMED, BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStateMachine.canTransition(BookingStatus.WAITLISTED, BookingStatus.CONFIRMED)).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThat(BookingStateMachine.canTransition(BookingStatus.CONFIRMED, BookingStatus.HELD)).isFalse();
        assertThat(BookingStateMachine.canTransition(BookingStatus.EXPIRED, BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStateMachine.canTransition(BookingStatus.CANCELLED, BookingStatus.CONFIRMED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (BookingStatus target : BookingStatus.values()) {
            assertThat(BookingStateMachine.canTransition(BookingStatus.CANCELLED, target)).isFalse();
            assertThat(BookingStateMachine.canTransition(BookingStatus.EXPIRED, target)).isFalse();
        }
    }

    @Test
    void bookingTransitionToEnforcesTheStateMachine() {
        Booking booking = new Booking("PNRSTATE00001", null, null, null,
                BookingStatus.HELD, new BigDecimal("100.00"), null);

        booking.transitionTo(BookingStatus.CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // CONFIRMED -> HELD is illegal and must be impossible to perform.
        assertThatThrownBy(() -> booking.transitionTo(BookingStatus.HELD))
                .isInstanceOf(InvalidBookingStateException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
