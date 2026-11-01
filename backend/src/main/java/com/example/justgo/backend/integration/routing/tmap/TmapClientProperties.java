package com.example.justgo.backend.integration.routing.tmap;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TMAP 비밀키와 네트워크 제한을 환경변수에서 바인딩하는 설정입니다.
 */
@Validated
@ConfigurationProperties(prefix = "integration.tmap")
public class TmapClientProperties {

    @NotBlank
    @Pattern(regexp = "https://.+", message = "TMAP 기본 URL은 HTTPS여야 합니다.")
    private String baseUrl = "https://apis.openapi.sk.com";

    @NotBlank
    private String appKey;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    @Min(1)
    @Max(100)
    private int dailyCallLimit = 90;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getDailyCallLimit() {
        return dailyCallLimit;
    }

    public void setDailyCallLimit(int dailyCallLimit) {
        this.dailyCallLimit = dailyCallLimit;
    }
}
