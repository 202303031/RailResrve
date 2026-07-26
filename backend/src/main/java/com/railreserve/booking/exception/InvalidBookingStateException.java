package com.railreserve.booking.exception;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** An operation was attempted from a booking state that does not allow it. Maps to 409. */
public class InvalidBookingStateException extends ApiException {

    public InvalidBookingStateException(String message) {
        super(ErrorCode.ILLEGAL_BOOKING_STATE, message);
    }
}
