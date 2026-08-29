package com.oj.runner.web;

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
import java.util.regex.Pattern;

/** Preserves the Worker request id in Runner logs without reading request bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RunnerCorrelationFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Request-ID";
    private static final Pattern SAFE_CORRELATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validUuid(request.getHeader(HEADER)) ? request.getHeader(HEADER) : UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null && !SAFE_CORRELATION.matcher(correlationId).matches()) correlationId = null;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            if (correlationId != null) MDC.put("correlationId", correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("correlationId");
        }
    }

    private boolean validUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
