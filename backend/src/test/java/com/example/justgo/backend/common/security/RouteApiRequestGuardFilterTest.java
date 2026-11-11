package com.example.justgo.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 추천 API의 인증·호출량·동시 실행·본문 크기 제한을 검증합니다. */
class RouteApiRequestGuardFilterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-07T00:00:00Z"),
            ZoneOffset.UTC);

    /** 보호가 활성화되면 토큰 없는 요청을 서비스 호출 전에 거부합니다. */
    @Test
    void rejectsMissingAccessTokenWithoutExposingExpectedToken() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("TEST_CLIENT_TOKEN_1234567890_ABCDE");
        var filter = newFilter(properties);
        var request = routeRequest("192.0.2.10");
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).contains("ROUTE_API_UNAUTHORIZED");
        assertThat(response.getContentAsString()).doesNotContain(properties.getAccessToken());
        assertThat(chainCalls).hasValue(0);
    }

    /** 올바른 별도 접근 토큰은 실제 추천 처리로 전달됩니다. */
    @Test
    void acceptsValidAccessToken() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("TEST_CLIENT_TOKEN_1234567890_ABCDE");
        var filter = newFilter(properties);
        var request = routeRequest("192.0.2.10");
        request.addHeader(RouteApiRequestGuardFilter.ACCESS_TOKEN_HEADER, properties.getAccessToken());
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(chainCalls).hasValue(1);
    }

    /** 위조 가능한 전달 헤더가 달라도 직접 연결 주소가 같으면 같은 제한을 적용합니다. */
    @Test
    void rateLimitsByContainerRemoteAddressAndIgnoresForwardedHeader() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setRequestsPerMinute(1);
        var filter = newFilter(properties);
        AtomicInteger chainCalls = new AtomicInteger();

        var firstRequest = routeRequest("192.0.2.20");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilter(
                firstRequest,
                new MockHttpServletResponse(),
                countingChain(chainCalls));

        var secondRequest = routeRequest("192.0.2.20");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, countingChain(chainCalls));

        assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondResponse.getContentAsString()).contains("ROUTE_API_RATE_LIMITED");
        assertThat(chainCalls).hasValue(1);
    }

    /** Content-Length가 제한을 넘는 요청은 JSON 역직렬화 전에 거부합니다. */
    @Test
    void rejectsOversizedRequestBody() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setMaxRequestBodyBytes(512);
        var filter = newFilter(properties);
        var request = routeRequest("192.0.2.30");
        request.setContent(new byte[513]);
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(chainCalls).hasValue(0);
    }

    /** 길이 헤더가 없는 chunked 본문도 실제 바이트 수로 동일하게 제한합니다. */
    @Test
    void rejectsOversizedRequestBodyWithoutContentLength() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setMaxRequestBodyBytes(512);
        var filter = newFilter(properties);
        var request = new MockHttpServletRequest(
                "POST",
                "/api/v1/routes/recommendations") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setRemoteAddr("192.0.2.31");
        request.setContentType("application/json");
        request.setContent(new byte[513]);
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(chainCalls).hasValue(0);
    }

    /** 제한 안의 본문은 길이 헤더가 없어도 다음 계층에서 원문 그대로 다시 읽을 수 있습니다. */
    @Test
    void replaysAcceptedBodyWithoutContentLength() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setMaxRequestBodyBytes(512);
        var filter = newFilter(properties);
        byte[] body = "{\"desiredMinutes\":60}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = new MockHttpServletRequest(
                "POST",
                "/api/v1/routes/recommendations") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setRemoteAddr("192.0.2.32");
        request.setContentType("application/json");
        request.setContent(body);
        var response = new MockHttpServletResponse();
        AtomicReference<byte[]> replayedBody = new AtomicReference<>();

        filter.doFilter(request, response, (guardedRequest, ignored) ->
                replayedBody.set(guardedRequest.getInputStream().readAllBytes()));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(replayedBody.get()).isEqualTo(body);
    }

    /** 처리 중인 추천 수가 상한에 도달하면 추가 외부 호출을 시작하지 않습니다. */
    @Test
    void rejectsConcurrentRequestAboveConfiguredLimit() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setMaxConcurrentRequests(1);
        var filter = newFilter(properties);
        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        AtomicReference<Throwable> asyncFailure = new AtomicReference<>();

        Thread firstThread = Thread.ofVirtual().start(() -> {
            try {
                filter.doFilter(
                        routeRequest("192.0.2.40"),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                            firstRequestEntered.countDown();
                            try {
                                if (!releaseFirstRequest.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("첫 요청 해제 대기 시간이 초과됐습니다.");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        });
            } catch (Throwable throwable) {
                asyncFailure.set(throwable);
            }
        });

        assertThat(firstRequestEntered.await(5, TimeUnit.SECONDS)).isTrue();
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(
                routeRequest("192.0.2.41"),
                secondResponse,
                (request, response) -> { });
        releaseFirstRequest.countDown();
        firstThread.join(5_000);

        assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(secondResponse.getContentAsString()).contains("ROUTE_API_BUSY");
        assertThat(firstThread.isAlive()).isFalse();
        assertThat(asyncFailure.get()).isNull();
    }

    /** 비용이 들지 않는 health 요청은 추천 API 제한에서 제외합니다. */
    @Test
    void skipsUnrelatedEndpoint() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("TEST_CLIENT_TOKEN_1234567890_ABCDE");
        var filter = newFilter(properties);
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(chainCalls).hasValue(1);
    }

    /** 외부 공공데이터를 호출할 수 있는 후보 조회도 같은 접근 토큰으로 보호합니다. */
    @Test
    void protectsCandidateLookupEndpoint() throws Exception {
        RouteApiSecurityProperties properties = defaultProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("TEST_CLIENT_TOKEN_1234567890_ABCDE");
        var filter = newFilter(properties);
        var request = new MockHttpServletRequest("GET", "/api/v1/places/candidates");
        request.setRemoteAddr("192.0.2.50");
        var response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, countingChain(chainCalls));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(chainCalls).hasValue(0);
    }

    /** 테스트마다 예측 가능한 기본 보호 설정을 만듭니다. */
    private RouteApiSecurityProperties defaultProperties() {
        RouteApiSecurityProperties properties = new RouteApiSecurityProperties();
        properties.setRequestsPerMinute(4);
        properties.setMaxConcurrentRequests(2);
        properties.setMaxRequestBodyBytes(8_192);
        properties.setMaxTrackedClients(128);
        return properties;
    }

    /** 실제 서비스와 분리된 고정 시계 필터를 만듭니다. */
    private RouteApiRequestGuardFilter newFilter(RouteApiSecurityProperties properties) {
        return new RouteApiRequestGuardFilter(properties, new ObjectMapper(), FIXED_CLOCK);
    }

    /** 필터 체인 호출 횟수만 기록합니다. */
    private FilterChain countingChain(AtomicInteger chainCalls) {
        return (request, response) -> chainCalls.incrementAndGet();
    }

    /** 외부 경로 호출을 발생시키는 추천 POST 요청을 만듭니다. */
    private MockHttpServletRequest routeRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/routes/recommendations");
        request.setRemoteAddr(remoteAddress);
        request.setContentType("application/json");
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
