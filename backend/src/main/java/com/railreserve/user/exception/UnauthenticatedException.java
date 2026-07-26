package com.railreserve.user.exception;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** No authenticated user could be resolved for a request that requires one. Maps to 401. */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
