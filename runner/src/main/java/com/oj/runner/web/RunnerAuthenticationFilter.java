package com.oj.runner.web;

import com.oj.runner.config.RunnerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RunnerAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final byte[] expectedTokenDigest;
    private final RunnerResponseWriter responseWriter;

    public RunnerAuthenticationFilter(RunnerProperties properties, RunnerResponseWriter responseWriter) {
        String token = properties.getToken();
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw new IllegalStateException("RUNNER_TOKEN must be non-empty and contain no line breaks");
        }
        expectedTokenDigest = digest(token);
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/v1/jobs".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String provided = authorization != null && authorization.startsWith(PREFIX)
                ? authorization.substring(PREFIX.length())
                : "";
        if (provided.isEmpty() || !MessageDigest.isEqual(expectedTokenDigest, digest(provided))) {
            responseWriter.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Runner authentication failed");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private byte[] digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
