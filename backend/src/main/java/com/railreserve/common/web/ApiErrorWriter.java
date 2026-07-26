package com.railreserve.common.web;

import com.railreserve.common.api.ApiError;
import com.railreserve.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes the standard {@link ApiResponse} error envelope directly to the servlet response. Used
 * by the security filter chain, which runs before {@code @RestControllerAdvice} can catch anything.
 */
public final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper,
                             HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(ApiError.of(code, message))));
    }
}
