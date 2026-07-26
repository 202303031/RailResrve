package com.railreserve.common.exception;

/** Thrown when a request is well-formed but violates a business rule. Maps to 400/409. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
