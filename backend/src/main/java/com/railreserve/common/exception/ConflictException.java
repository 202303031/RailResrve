package com.railreserve.common.exception;

/**
 * Thrown when a request conflicts with the current state of a resource -- e.g. a seat that
 * was taken by someone else, or a duplicate idempotency key. Maps to HTTP 409.
 */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
