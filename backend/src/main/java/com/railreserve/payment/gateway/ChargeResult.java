package com.railreserve.payment.gateway;

/**
 * Result of a charge. {@code gatewayRef} is the provider's reference for the transaction; it is
 * what we later pass to {@link PaymentGateway#refund(String)} to compensate.
 */
public record ChargeResult(ChargeOutcome outcome, String gatewayRef) {

    public boolean isApproved() {
        return outcome == ChargeOutcome.APPROVED;
    }
}
