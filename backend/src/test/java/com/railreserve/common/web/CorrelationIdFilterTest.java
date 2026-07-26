package com.railreserve.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Captures the MDC value visible to downstream handlers while the chain runs. */
    private AtomicReference<String> runAndCaptureMdc(MockHttpServletRequest request,
                                                     MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return seen;
    }

    @Test
    void generatesACorrelationIdWhenNoneIsSupplied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = runAndCaptureMdc(new MockHttpServletRequest(), response);

        assertThat(seen.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seen.get());
    }

    @Test
    void honoursAValidInboundCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = runAndCaptureMdc(request, response);

        assertThat(seen.get()).isEqualTo("abc-123_DEF");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_DEF");
    }

    @Test
    void rejectsAHostileHeaderAndGeneratesAFreshId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad\nvalue INJECTED");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = runAndCaptureMdc(request, response);

        assertThat(seen.get()).doesNotContain("INJECTED").doesNotContain("\n");
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        runAndCaptureMdc(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
