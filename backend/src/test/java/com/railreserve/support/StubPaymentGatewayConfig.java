package com.railreserve.support;

import com.railreserve.payment.gateway.ChargeOutcome;
import com.railreserve.payment.gateway.ChargeRequest;
import com.railreserve.payment.gateway.ChargeResult;
import com.railreserve.payment.gateway.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Default payment gateway for the broad integration-test suite: it approves every charge in-process,
 * so the happy-path booking flow still exercises the full confirm saga (prepare → charge → finalize
 * → record SUCCESS payment) without any real network call. The dedicated
 * {@code PaymentSagaIntegrationTest} does <em>not</em> import this — it drives the real
 * {@code HttpPaymentGateway} against WireMock to prove the failure and compensation paths.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubPaymentGatewayConfig {

    @Bean
    @Primary
    public PaymentGateway stubPaymentGateway() {
        return new PaymentGateway() {
            @Override
            public ChargeResult charge(ChargeRequest request) {
                return new ChargeResult(ChargeOutcome.APPROVED, "stub-" + request.idempotencyKey());
            }

            @Override
            public void refund(String gatewayRef) {
                // no-op
            }
        };
    }
}
