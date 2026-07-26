package com.railreserve.security;

import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;

/** Wrong email or password on login. Maps to HTTP 401. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
