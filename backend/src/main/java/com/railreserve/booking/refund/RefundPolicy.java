package com.railreserve.booking.refund;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Strategy for computing a cancellation refund. Swapping the implementation (or picking one per
 * fare class) changes the refund rules without touching the cancellation flow.
 */
public interface RefundPolicy {

    BigDecimal computeRefund(BigDecimal fare, Duration timeUntilDeparture);
}
