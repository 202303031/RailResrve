package com.railreserve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, each mapped to the HTTP status it should produce.
 * The enum name is what clients receive as the machine-readable {@code code}.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    STATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT),
    HOLD_EXPIRED(HttpStatus.GONE),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    ILLEGAL_BOOKING_STATE(HttpStatus.CONFLICT),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
