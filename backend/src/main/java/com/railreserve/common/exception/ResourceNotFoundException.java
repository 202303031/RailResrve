package com.railreserve.common.exception;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
