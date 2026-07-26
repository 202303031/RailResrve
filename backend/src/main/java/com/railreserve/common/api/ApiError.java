package com.railreserve.common.api;

import java.util.List;

/**
 * Machine-readable error payload carried inside {@link ApiResponse}. {@code code} is a
 * stable enum name (see {@code ErrorCode}); {@code fieldErrors} is populated for
 * validation failures.
 */
public record ApiError(String code, String message, List<FieldError> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
