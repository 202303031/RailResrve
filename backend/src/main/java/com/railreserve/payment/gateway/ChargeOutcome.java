package com.railreserve.payment.gateway;

/** The business result of a charge attempt (a technical failure throws instead). */
public enum ChargeOutcome {
    APPROVED,
    DECLINED
}
