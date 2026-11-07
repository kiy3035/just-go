package com.example.justgo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 요청 추적값이 로그 주입 없이 전달되고 요청 종료 후 정리되는지 검증합니다. */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    /** 안전한 클라이언트 추적값은 응답과 처리 중 MDC에 동일하게 유지합니다. */
    @Test
    void preservesSafeTraceIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "android-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuringRequest = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                traceIdDuringRequest.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo("android-request-123");
        assertThat(traceIdDuringRequest).hasValue("android-request-123");
        assertThat(MDC.get("traceId")).isNull();
    }

    /** 허용되지 않은 문자가 있는 추적값은 로그에 전달하지 않고 새 UUID로 교체합니다. */
    @Test
    void replacesUnsafeTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "invalid trace<script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuringRequest = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                traceIdDuringRequest.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        String generatedTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(generatedTraceId)
                .isNotEqualTo("invalid trace<script>")
                .matches("[a-f0-9-]{36}");
        assertThat(traceIdDuringRequest).hasValue(generatedTraceId);
        assertThat(MDC.get("traceId")).isNull();
    }
}
