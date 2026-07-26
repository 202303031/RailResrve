package com.railreserve.payment.gateway;

import java.math.BigDecimal;

/**
 * Command to capture a payment. {@code idempotencyKey} (the booking PNR) makes a retried charge
 * safe: the provider returns the original result instead of capturing again.
 */
public record ChargeRequest(String idempotencyKey, BigDecimal amount, String currency, String paymentToken) {
}
