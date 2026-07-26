package com.railreserve.booking.service;

import com.railreserve.booking.web.dto.ConfirmResponse;

import java.math.BigDecimal;

/**
 * Snapshot produced by the first step of the confirm saga and carried across the (non-transactional)
 * payment charge into the finalize step.
 *
 * @param alreadyConfirmed true for an idempotent replay — the caller returns {@link #response}
 *                         without charging again
 * @param response         the confirm response for the already-confirmed case (else null)
 * @param pnr              the booking PNR, used as the charge idempotency key
 * @param totalFare        the amount to charge (else null)
 */
public record PreparedConfirm(boolean alreadyConfirmed, ConfirmResponse response, String pnr, BigDecimal totalFare) {

    static PreparedConfirm alreadyConfirmed(ConfirmResponse response) {
        return new PreparedConfirm(true, response, response.pnr(), null);
    }

    static PreparedConfirm pending(String pnr, BigDecimal totalFare) {
        return new PreparedConfirm(false, null, pnr, totalFare);
    }
}
