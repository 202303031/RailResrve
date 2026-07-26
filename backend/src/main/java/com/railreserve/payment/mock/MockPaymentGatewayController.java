package com.railreserve.payment.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A stand-in for an external payment provider (PSP). It is deliberately decoupled from the rest of
 * the application — it shares no code with the booking domain and keeps its own in-memory ledger —
 * so it could be lifted out into its own deployable without change. It exists only to make local
 * and Docker runs work end to end; the automated failure-path tests use WireMock instead.
 *
 * <p>Behaviour is driven by the {@code paymentToken}, which lets a manual demo exercise each branch:
 * a token containing {@code "decline"} is declined; one containing {@code "fail"} returns a 502
 * (technical failure); anything else is approved. Charges are <b>idempotent</b> by the request's
 * idempotency key, so a duplicate/replayed charge returns the original reference and never captures
 * twice — the same guarantee a real PSP gives.
 */
@RestController
@RequestMapping("/mock-gateway/v1/charges")
@ConditionalOnProperty(prefix = "railreserve.payment.mock-gateway", name = "enabled", matchIfMissing = true)
public class MockPaymentGatewayController {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayController.class);

    // The provider's ledger, keyed by idempotency key (for replay) and by reference (for refund).
    private final Map<String, ChargeResponse> ledgerByKey = new ConcurrentHashMap<>();
    private final Set<String> knownReferences = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong(1000);

    @PostMapping
    public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest request) {
        String token = request.paymentToken() == null ? "" : request.paymentToken();
        if (token.contains("fail")) {
            log.warn("Mock gateway simulating a technical failure for key {}", request.idempotencyKey());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        // Idempotency: the same key always yields the same charge, never a second capture.
        ChargeResponse existing = ledgerByKey.get(request.idempotencyKey());
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }

        String status = token.contains("decline") ? "DECLINED" : "APPROVED";
        String reference = "ch_" + sequence.incrementAndGet();
        ChargeResponse charge = new ChargeResponse(reference, status);
        ledgerByKey.put(request.idempotencyKey(), charge);
        knownReferences.add(reference);
        log.info("Mock gateway {} charge {} for key {}", status, reference, request.idempotencyKey());
        return ResponseEntity.ok(charge);
    }

    @PostMapping("/{reference}/refund")
    public ResponseEntity<Void> refund(@PathVariable String reference) {
        if (knownReferences.contains(reference)) {
            log.info("Mock gateway refunded charge {}", reference);
        }
        // Refunding an unknown reference is a no-op success, so the caller's compensation is safe.
        return ResponseEntity.ok().build();
    }

    public record ChargeRequest(String idempotencyKey, BigDecimal amount, String currency, String paymentToken) {
    }

    public record ChargeResponse(String reference, String status) {
    }
}
