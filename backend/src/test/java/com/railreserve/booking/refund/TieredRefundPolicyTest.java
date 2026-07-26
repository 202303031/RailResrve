package com.railreserve.booking.refund;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TieredRefundPolicyTest {

    private final TieredRefundPolicy policy = new TieredRefundPolicy();
    private final BigDecimal fare = new BigDecimal("1000.00");

    @Test
    void ninetyPercentWhenCancelledWellBeforeDeparture() {
        assertThat(policy.computeRefund(fare, Duration.ofHours(72))).isEqualByComparingTo("900.00");
    }

    @Test
    void fiftyPercentBetween12And48Hours() {
        assertThat(policy.computeRefund(fare, Duration.ofHours(24))).isEqualByComparingTo("500.00");
    }

    @Test
    void twentyFivePercentBetween4And12Hours() {
        assertThat(policy.computeRefund(fare, Duration.ofHours(6))).isEqualByComparingTo("250.00");
    }

    @Test
    void nothingWithinFourHours() {
        assertThat(policy.computeRefund(fare, Duration.ofHours(2))).isEqualByComparingTo("0.00");
    }

    @Test
    void nothingAfterDeparture() {
        assertThat(policy.computeRefund(fare, Duration.ofHours(-3))).isEqualByComparingTo("0.00");
    }
}
