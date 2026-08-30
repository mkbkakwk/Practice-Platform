package com.oj.runner.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.slf4j.MDC.get;

class RunnerCorrelationFilterTest {
    private static final String REQUEST_ID = "11111111-1111-4111-8111-111111111111";
    private final RunnerCorrelationFilter filter = new RunnerCorrelationFilter();

    @Test
    void propagatesSafeWorkerRequestAndCorrelationIdsWithoutLeakingMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
        request.addHeader("X-Request-ID", REQUEST_ID);
        request.addHeader("X-Correlation-ID", "submission-42.safe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertEquals(REQUEST_ID, get("requestId"));
            assertEquals("submission-42.safe", get("correlationId"));
        });

        assertEquals(REQUEST_ID, response.getHeader("X-Request-ID"));
        assertEquals(null, get("requestId"));
        assertEquals(null, get("correlationId"));
    }

    @Test
    void replacesUnsafeHeadersAndDoesNotPlaceThemInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
        request.addHeader("X-Request-ID", "not-a-uuid");
        request.addHeader("X-Correlation-ID", "unsafe\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertNotNull(get("requestId"));
            assertEquals(null, get("correlationId"));
        });

        assertEquals(36, response.getHeader("X-Request-ID").length());
        UUID.fromString(response.getHeader("X-Request-ID"));
        assertEquals(null, get("requestId"));
        assertEquals(null, get("correlationId"));
    }
}
