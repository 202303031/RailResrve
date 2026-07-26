package com.railreserve.common.web;

import com.railreserve.common.api.ApiError;
import com.railreserve.common.api.ApiResponse;
import com.railreserve.common.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception into the standard {@link ApiResponse} envelope so clients always
 * receive a consistent, machine-readable error shape. Domain errors ({@link ApiException})
 * map to their declared status; framework/validation errors are translated explicitly; and
 * anything unexpected becomes a 500 without leaking internal detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        HttpStatus status = ex.getErrorCode().getStatus();
        if (status.is5xxServerError()) {
            log.error("Server-side API exception", ex);
        }
        return build(status, ex.getErrorCode().name(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", ex.getMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "No handler for " + ex.getResourcePath(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // A unique/foreign-key/check constraint the service layer did not translate itself.
        return build(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "The request conflicts with existing data", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message,
                                                    List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(ApiResponse.failure(new ApiError(code, message, fields)));
    }

    private static String lastNode(String propertyPath) {
        int i = propertyPath.lastIndexOf('.');
        return i >= 0 ? propertyPath.substring(i + 1) : propertyPath;
    }
}
