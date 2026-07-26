package com.railreserve.payment.gateway;

import com.railreserve.common.web.CorrelationIdFilter;
import com.railreserve.payment.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Real adapter to the payment provider over HTTP. It is the class the confirm saga uses in
 * production; in tests it is either replaced by a fake or pointed at a WireMock server so the
 * failure paths (timeout, 5xx) can be exercised deterministically.
 *
 * <p>Connect and read timeouts are set explicitly: a hung provider must surface as a
 * {@link PaymentGatewayException} quickly rather than pinning a request thread. The remote call is
 * made <b>outside</b> any database transaction (the saga orchestrates that), so a slow gateway
 * never holds a DB connection or row lock open.
 */
@Component
public class HttpPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentGateway.class);

    private final RestClient restClient;

    public HttpPaymentGateway(PaymentProperties properties) {
        PaymentProperties.Gateway gateway = properties.gateway();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(gateway.connectTimeoutMs());
        requestFactory.setReadTimeout(gateway.readTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(gateway.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        try {
            GatewayChargeResponse response = restClient.post()
                    .uri("/v1/charges")
                    .headers(this::propagateCorrelationId)
                    .body(request)
                    .retrieve()
                    .body(GatewayChargeResponse.class);
            if (response == null || response.status() == null) {
                throw new PaymentGatewayException("Gateway returned an empty charge response", null);
            }
            ChargeOutcome outcome = "APPROVED".equalsIgnoreCase(response.status())
                    ? ChargeOutcome.APPROVED : ChargeOutcome.DECLINED;
            return new ChargeResult(outcome, response.reference());
        } catch (ResourceAccessException e) {
            // connection refused or read timeout — outcome is indeterminate
            throw new PaymentGatewayException("Payment gateway unreachable or timed out", e);
        } catch (RestClientException e) {
            // includes 5xx from the provider
            throw new PaymentGatewayException("Payment gateway call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String gatewayRef) {
        try {
            restClient.post()
                    .uri("/v1/charges/{ref}/refund", gatewayRef)
                    .headers(this::propagateCorrelationId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Refund of charge {} failed and needs manual reconciliation", gatewayRef, e);
            throw new PaymentGatewayException("Refund call failed: " + e.getMessage(), e);
        }
    }

    /** Forward this request's correlation id to the provider so one trace spans both services. */
    private void propagateCorrelationId(org.springframework.http.HttpHeaders headers) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.HEADER, correlationId);
        }
    }

    /** Wire shape of the provider's charge response. */
    private record GatewayChargeResponse(String reference, String status) {
    }
}
