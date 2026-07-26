package com.railreserve.booking.service;

import com.railreserve.scheduling.domain.TravelClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FareCalculatorTest {

    private final FareCalculator calculator = new FareCalculator();

    @Test
    void farePerSeatIsTheConfiguredBaseFareForTheClass() {
        assertThat(calculator.farePerSeat(TravelClass.SLEEPER)).isEqualByComparingTo("450.00");
        assertThat(calculator.farePerSeat(TravelClass.AC_1_TIER)).isEqualByComparingTo("2900.00");
    }

    @Test
    void totalFareMultipliesByTheSeatCount() {
        assertThat(calculator.totalFare(TravelClass.SLEEPER, 3)).isEqualByComparingTo("1350.00");
    }

    @Test
    void totalFareForZeroSeatsIsZero() {
        assertThat(calculator.totalFare(TravelClass.AC_2_TIER, 0)).isEqualByComparingTo("0.00");
    }
}
