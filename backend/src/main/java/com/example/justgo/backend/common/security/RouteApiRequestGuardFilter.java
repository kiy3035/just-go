package com.example.justgo.backend.common.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.example.justgo.backend.common.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 비용이 큰 추천 API 앞에서 접근 토큰, 요청 크기, 호출 속도와 동시 실행 수를 제한합니다.
 * 클라이언트 주소는 메모리에서만 사용하고 로그나 DB에 저장하지 않습니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RouteApiRequestGuardFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_HEADER = "X-Just-Go-Client-Token";
    private static final String ROUTE_RECOMMENDATION_PATH = "/api/v1/routes/recommendations";
    private static final String PLACE_CANDIDATE_PATH = "/api/v1/places/candidates";
    private static final long WINDOW_MILLIS = 60_000L;

    private final RouteApiSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Semaphore concurrentRequests;
    private final byte[] expectedAccessTokenHash;
    private final ConcurrentHashMap<String, ClientWindow> clientWindows = new ConcurrentHashMap<>();

    /** 테스트 가능한 시계와 설정으로 추천 API 보호 필터를 구성합니다. */
    RouteApiRequestGuardFilter(
            RouteApiSecurityProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.concurrentRequests = new Semaphore(properties.getMaxConcurrentRequests());
        this.expectedAccessTokenHash = hashToken(properties.getAccessToken());
    }

    /** 외부 호출이 가능한 추천·장소 후보 API만 비용 제한 대상으로 삼습니다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String routePath = request.getContextPath() + ROUTE_RECOMMENDATION_PATH;
        String candidatePath = request.getContextPath() + PLACE_CANDIDATE_PATH;
        boolean routeRequest = HttpMethod.POST.matches(request.getMethod())
                && routePath.equals(request.getRequestURI());
        boolean candidateRequest = HttpMethod.GET.matches(request.getMethod())
                && candidatePath.equals(request.getRequestURI());
        return !routeRequest && !candidateRequest;
    }

    /** 보호 단계를 통과한 요청만 실제 추천 서비스와 TMAP Provider로 전달합니다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!hasValidAccessToken(request)) {
            writeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "ROUTE_API_UNAUTHORIZED",
                    "추천 API 접근 권한을 확인해 주세요.");
            return;
        }
        if (HttpMethod.POST.matches(request.getMethod())
                && request.getContentLengthLong() > properties.getMaxRequestBodyBytes()) {
            writeRequestTooLarge(response);
            return;
        }
        HttpServletRequest guardedRequest = request;
        if (HttpMethod.POST.matches(request.getMethod())) {
            byte[] requestBody = readLimitedBody(
                    request, properties.getMaxRequestBodyBytes());
            if (requestBody == null) {
                writeRequestTooLarge(response);
                return;
            }
            guardedRequest = new CachedBodyRequest(request, requestBody);
        }
        if (!tryAcquireClientWindow(resolveClientKey(request))) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            writeError(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "ROUTE_API_RATE_LIMITED",
                    "추천 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (!concurrentRequests.tryAcquire()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            writeError(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "ROUTE_API_BUSY",
                    "추천 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }

        try {
            filterChain.doFilter(guardedRequest, response);
        } finally {
            concurrentRequests.release();
        }
    }

    /** Content-Length 유무와 관계없이 실제 본문을 설정 상한까지만 메모리에 보관합니다. */
    private byte[] readLimitedBody(HttpServletRequest request, int maximumBytes) throws IOException {
        try (var input = request.getInputStream();
                var output = new ByteArrayOutputStream(Math.min(maximumBytes, 1_024))) {
            byte[] buffer = new byte[1_024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() > maximumBytes - count) {
                    return null;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        writeError(
                response,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_TOO_LARGE",
                "추천 요청 본문이 허용 크기를 초과했습니다.");
    }

    /** 설정된 토큰을 일정 시간 비교해 단순 문자열 비교의 조기 종료를 피합니다. */
    private boolean hasValidAccessToken(HttpServletRequest request) {
        if (!properties.isAccessTokenRequired()) {
            return true;
        }
        String providedToken = request.getHeader(ACCESS_TOKEN_HEADER);
        if (providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedAccessTokenHash, hashToken(providedToken));
    }

    /** 입력 길이와 무관한 고정 길이 해시를 비교해 토큰 길이 추측 가능성을 줄입니다. */
    private byte[] hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("필수 SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    /** 전달 헤더를 신뢰하지 않고 서블릿 컨테이너가 확인한 직접 연결 주소만 사용합니다. */
    private String resolveClientKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    /** 고정 1분 창에서 클라이언트별 허용 횟수를 원자적으로 차감합니다. */
    private boolean tryAcquireClientWindow(String clientKey) {
        long currentWindow = clock.millis() / WINDOW_MILLIS;
        ClientWindow existingWindow = clientWindows.get(clientKey);
        if (existingWindow == null && clientWindows.size() >= properties.getMaxTrackedClients()) {
            removeExpiredWindows(currentWindow);
            if (clientWindows.size() >= properties.getMaxTrackedClients()) {
                return false;
            }
        }

        ClientWindow window = clientWindows.computeIfAbsent(
                clientKey,
                ignored -> new ClientWindow(currentWindow));
        synchronized (window) {
            if (window.windowNumber != currentWindow) {
                window.windowNumber = currentWindow;
                window.requestCount = 0;
            }
            if (window.requestCount >= properties.getRequestsPerMinute()) {
                return false;
            }
            window.requestCount++;
            return true;
        }
    }

    /** 오래된 주소 항목을 제거해 임의 주소 입력으로 메모리가 무한 증가하지 않게 합니다. */
    private void removeExpiredWindows(long currentWindow) {
        clientWindows.entrySet().removeIf(entry -> entry.getValue().windowNumber < currentWindow);
    }

    /** 인증 토큰이나 내부 제한값을 노출하지 않는 공통 JSON 오류를 작성합니다. */
    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(code, message, Map.of()));
    }

    /** 한 클라이언트의 현재 1분 창과 사용 횟수만 메모리에 보관합니다. */
    private static final class ClientWindow {

        private long windowNumber;
        private int requestCount;

        private ClientWindow(long windowNumber) {
            this.windowNumber = windowNumber;
        }
    }

    /** 한 번 검증한 작은 JSON 본문을 Spring MVC가 다시 읽을 수 있게 합니다. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            var charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : java.nio.charset.Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedBodyInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private CachedBodyInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            try {
                if (!isFinished()) {
                    listener.onDataAvailable();
                }
                if (isFinished()) {
                    listener.onAllDataRead();
                }
            } catch (IOException exception) {
                listener.onError(exception);
            }
        }
    }
}
