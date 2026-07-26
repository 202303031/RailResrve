package com.railreserve.booking.service;

import com.railreserve.scheduling.domain.TravelClass;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Computes fares. Currently a flat per-class base fare; a distance-based tariff (using the
 * boarding/alighting stops) would slot in here without touching callers.
 */
@Component
public class FareCalculator {

    private static final BigDecimal DEFAULT_FARE = new BigDecimal("500.00");

    private static final Map<TravelClass, BigDecimal> BASE_FARE = Map.of(
            TravelClass.SECOND_SITTING, new BigDecimal("250.00"),
            TravelClass.SLEEPER, new BigDecimal("450.00"),
            TravelClass.CHAIR_CAR, new BigDecimal("700.00"),
            TravelClass.AC_3_TIER, new BigDecimal("1150.00"),
            TravelClass.EXECUTIVE_CHAIR, new BigDecimal("1400.00"),
            TravelClass.AC_2_TIER, new BigDecimal("1750.00"),
            TravelClass.AC_1_TIER, new BigDecimal("2900.00"));

    public BigDecimal farePerSeat(TravelClass travelClass) {
        return BASE_FARE.getOrDefault(travelClass, DEFAULT_FARE);
    }

    public BigDecimal totalFare(TravelClass travelClass, int seatCount) {
        return farePerSeat(travelClass).multiply(BigDecimal.valueOf(seatCount));
    }
}
