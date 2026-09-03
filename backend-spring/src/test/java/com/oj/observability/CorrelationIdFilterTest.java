package com.oj.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.slf4j.MDC.get;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesValidRequestIdAndPlacesItInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        request.addHeader(CorrelationIdFilter.HEADER, "deploy-42.safe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertEquals("deploy-42.safe", get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("deploy-42.safe", response.getHeader(CorrelationIdFilter.HEADER));
        assertEquals(null, get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeIncomingValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        request.addHeader(CorrelationIdFilter.HEADER, "unsafe\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertNotNull(generated);
        assertEquals(36, generated.length());
    }
}
