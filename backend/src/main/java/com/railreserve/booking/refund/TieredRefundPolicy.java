package com.railreserve.booking.refund;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Default refund rule: the closer to departure you cancel, the less you get back.
 * <ul>
 *   <li>&ge; 48h before departure: 90% refund</li>
 *   <li>12h–48h: 50%</li>
 *   <li>4h–12h: 25%</li>
 *   <li>&lt; 4h or after departure: 0%</li>
 * </ul>
 */
@Component
public class TieredRefundPolicy implements RefundPolicy {

    @Override
    public BigDecimal computeRefund(BigDecimal fare, Duration timeUntilDeparture) {
        if (fare == null || fare.signum() <= 0 || timeUntilDeparture.isNegative()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long hours = timeUntilDeparture.toHours();
        BigDecimal rate;
        if (hours >= 48) {
            rate = new BigDecimal("0.90");
        } else if (hours >= 12) {
            rate = new BigDecimal("0.50");
        } else if (hours >= 4) {
            rate = new BigDecimal("0.25");
        } else {
            rate = BigDecimal.ZERO;
        }
        return fare.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
