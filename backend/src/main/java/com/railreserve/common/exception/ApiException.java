package com.railreserve.common.exception;

/**
 * Base type for all application exceptions that map to a clean HTTP error response.
 * Every subclass carries an {@link ErrorCode}, which determines the HTTP status and the
 * machine-readable code returned to the client.
 */
public abstract class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
