package com.railreserve.booking.service;

/**
 * Internal signal that a concurrent request carrying the same idempotency key won the race to
 * insert the booking. The booking service retries, and the retry's idempotency pre-check
 * returns the winning hold. This never reaches a client.
 */
public class ConcurrentDuplicateRequestException extends RuntimeException {

    public ConcurrentDuplicateRequestException() {
        super("Concurrent duplicate request");
    }
}
