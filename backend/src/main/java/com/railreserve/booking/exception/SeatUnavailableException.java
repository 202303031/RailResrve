package com.railreserve.booking.exception;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** One or more requested seats are no longer available. Maps to HTTP 409. */
public class SeatUnavailableException extends ApiException {

    public SeatUnavailableException(String message) {
        super(ErrorCode.SEAT_UNAVAILABLE, message);
    }
}
