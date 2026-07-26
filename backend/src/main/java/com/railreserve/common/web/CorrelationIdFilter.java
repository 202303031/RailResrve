package com.railreserve.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id and puts it in the logging {@link MDC}, so all log lines for
 * one request can be tied together (and followed across services — see how the payment gateway
 * forwards it). An inbound {@code X-Correlation-Id} is honoured (after sanitising, to prevent log
 * injection); otherwise a fresh id is generated. The id is echoed back on the response so a caller
 * can quote it in a bug report. Runs first ({@code HIGHEST_PRECEDENCE}) so even the security layer's
 * logs carry it, and the MDC is always cleared afterwards to avoid leaking onto a pooled thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Accept only a safe subset of characters so a hostile header can't forge log lines. */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        if (!trimmed.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return trimmed;
    }
}
