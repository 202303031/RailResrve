package com.railreserve.payment.gateway;

/**
 * A technical/transient failure calling the payment provider (timeout, 5xx, connection error).
 * The outcome is <b>indeterminate</b> — money may or may not have moved — so the booking saga must
 * not confirm, and any later compensation errs on the side of issuing a refund.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
