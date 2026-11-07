package com.example.justgo.backend.common.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 외부 경로 호출을 발생시키는 추천 API의 접근·자원 제한 설정입니다.
 * TMAP 키와 별개의 클라이언트 접근 토큰을 사용하며 원본 값은 로그에 남기지 않습니다.
 */
@Validated
@ConfigurationProperties(prefix = "justgo.security.route-api")
public class RouteApiSecurityProperties {

    private boolean accessTokenRequired;
    private String accessToken = "";

    @Min(1)
    @Max(60)
    private int requestsPerMinute = 4;

    @Min(1)
    @Max(10)
    private int maxConcurrentRequests = 2;

    @Min(512)
    @Max(65_536)
    private int maxRequestBodyBytes = 8_192;

    @Min(128)
    @Max(100_000)
    private int maxTrackedClients = 2_048;

    public boolean isAccessTokenRequired() {
        return accessTokenRequired;
    }

    public void setAccessTokenRequired(boolean accessTokenRequired) {
        this.accessTokenRequired = accessTokenRequired;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(int maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }
}
