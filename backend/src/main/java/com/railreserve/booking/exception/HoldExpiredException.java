package com.railreserve.booking.exception;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** The hold has already expired (or was released) and can no longer be confirmed. Maps to 410. */
public class HoldExpiredException extends ApiException {

    public HoldExpiredException(String message) {
        super(ErrorCode.HOLD_EXPIRED, message);
    }
}
