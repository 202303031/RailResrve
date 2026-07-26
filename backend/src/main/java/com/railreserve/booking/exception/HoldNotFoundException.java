package com.railreserve.booking.exception;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** No hold exists for the supplied hold id (or it belongs to another user). Maps to 404. */
public class HoldNotFoundException extends ApiException {

    public HoldNotFoundException(String message) {
        super(ErrorCode.HOLD_NOT_FOUND, message);
    }
}
