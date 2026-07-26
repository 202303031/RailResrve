package com.railreserve.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single response envelope for every endpoint: exactly one of {@code data} (on
 * success) or {@code error} (on failure) is present. Null fields are omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
