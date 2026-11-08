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
    @Pattern(
            regexp = "^(?!replace-with-local-tmap-app-key$).+$",
            message = "TMAP 예시 앱 키를 실제 값으로 변경해야 합니다.")
    private String appKey;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    @Min(1)
    @Max(100)
    private int dailyCallLimit = 90;

    @NotNull
    private Duration routeCacheTtl = Duration.ofMinutes(10);

    @Min(1)
    @Max(2048)
    private int routeCacheMaxEntries = 256;

    @NotNull
    private Duration poiCacheTtl = Duration.ofMinutes(10);

    @Min(1)
    @Max(33)
    private int poiRadiusKm = 33;

    @Min(1)
    @Max(200)
    private int poiPageSize = 100;

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

    public Duration getRouteCacheTtl() {
        return routeCacheTtl;
    }

    public void setRouteCacheTtl(Duration routeCacheTtl) {
        this.routeCacheTtl = routeCacheTtl;
    }

    public int getRouteCacheMaxEntries() {
        return routeCacheMaxEntries;
    }

    public void setRouteCacheMaxEntries(int routeCacheMaxEntries) {
        this.routeCacheMaxEntries = routeCacheMaxEntries;
    }

    public Duration getPoiCacheTtl() {
        return poiCacheTtl;
    }

    public void setPoiCacheTtl(Duration poiCacheTtl) {
        this.poiCacheTtl = poiCacheTtl;
    }

    public int getPoiRadiusKm() {
        return poiRadiusKm;
    }

    public void setPoiRadiusKm(int poiRadiusKm) {
        this.poiRadiusKm = poiRadiusKm;
    }

    public int getPoiPageSize() {
        return poiPageSize;
    }

    public void setPoiPageSize(int poiPageSize) {
        this.poiPageSize = poiPageSize;
    }
}
