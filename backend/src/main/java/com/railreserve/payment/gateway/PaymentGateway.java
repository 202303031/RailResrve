package com.railreserve.payment.gateway;

/**
 * Port to an external payment provider (a PSP). Deliberately small and technology-agnostic so the
 * booking saga depends only on this interface, never on HTTP details. The real adapter
 * ({@link HttpPaymentGateway}) talks to the provider over HTTP; tests swap in a fake or point the
 * adapter at WireMock.
 *
 * <p>Two failure modes are distinguished on purpose:
 * <ul>
 *   <li>a <b>declined</b> charge is a normal business outcome — {@link ChargeResult} carries
 *       {@link ChargeOutcome#DECLINED} and no exception is thrown;</li>
 *   <li>a <b>technical</b> failure (timeout, 5xx, unreachable) throws
 *       {@link PaymentGatewayException} — the caller does not know whether money moved, so it must
 *       treat the charge as indeterminate and not confirm.</li>
 * </ul>
 */
public interface PaymentGateway {

    /**
     * Attempt to capture a payment. The {@code idempotencyKey} (we use the booking PNR) lets the
     * provider deduplicate retries so the same charge is never captured twice.
     *
     * @throws PaymentGatewayException on a technical/transient failure (indeterminate outcome)
     */
    ChargeResult charge(ChargeRequest request);

    /**
     * Compensating action for the confirm saga: reverse a previously captured charge. Must be safe
     * to call with a reference that was never captured (the provider treats it as a no-op).
     *
     * @throws PaymentGatewayException on a technical/transient failure
     */
    void refund(String gatewayRef);
}
