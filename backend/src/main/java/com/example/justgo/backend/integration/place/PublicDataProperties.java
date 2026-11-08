package com.example.justgo.backend.integration.place;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 공공데이터포털 키와 호출·캐시 제한을 public-data 프로필에서만 바인딩합니다. */
@Validated
@ConfigurationProperties(prefix = "integration.public-data")
public class PublicDataProperties {

    @NotBlank
    @Pattern(
            regexp = "^(?!replace-with-public-data-service-key$).+$",
            message = "공공데이터 예시 키를 실제 값으로 변경해야 합니다.")
    private String parkApiKey;

    @NotBlank
    @Pattern(
            regexp = "^(?!replace-with-public-data-service-key$).+$",
            message = "TourAPI 예시 키를 실제 값으로 변경해야 합니다.")
    private String tourApiKey;

    @NotBlank
    @Pattern(regexp = "https://.+", message = "공원 데이터 기본 URL은 HTTPS여야 합니다.")
    private String parkBaseUrl = "https://api.data.go.kr/openapi";

    @NotBlank
    @Pattern(regexp = "https://.+", message = "TourAPI 기본 URL은 HTTPS여야 합니다.")
    private String tourBaseUrl = "https://apis.data.go.kr/B551011/KorService2";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(8);

    @NotNull
    private Duration parkCacheTtl = Duration.ofHours(6);

    @Min(1)
    @Max(50)
    private int parkMaxPages = 50;

    @Min(100)
    @Max(1000)
    private int parkPageSize = 1000;

    @Min(1)
    @Max(20000)
    private int tourRadiusMeters = 20000;

    @Min(1)
    @Max(1000)
    private int tourPageSize = 200;

    public String getParkApiKey() {
        return parkApiKey;
    }

    public void setParkApiKey(String parkApiKey) {
        this.parkApiKey = parkApiKey;
    }

    public String getTourApiKey() {
        return tourApiKey;
    }

    public void setTourApiKey(String tourApiKey) {
        this.tourApiKey = tourApiKey;
    }

    public String getParkBaseUrl() {
        return parkBaseUrl;
    }

    public void setParkBaseUrl(String parkBaseUrl) {
        this.parkBaseUrl = parkBaseUrl;
    }

    public String getTourBaseUrl() {
        return tourBaseUrl;
    }

    public void setTourBaseUrl(String tourBaseUrl) {
        this.tourBaseUrl = tourBaseUrl;
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

    public Duration getParkCacheTtl() {
        return parkCacheTtl;
    }

    public void setParkCacheTtl(Duration parkCacheTtl) {
        this.parkCacheTtl = parkCacheTtl;
    }

    public int getParkMaxPages() {
        return parkMaxPages;
    }

    public void setParkMaxPages(int parkMaxPages) {
        this.parkMaxPages = parkMaxPages;
    }

    public int getParkPageSize() {
        return parkPageSize;
    }

    public void setParkPageSize(int parkPageSize) {
        this.parkPageSize = parkPageSize;
    }

    public int getTourRadiusMeters() {
        return tourRadiusMeters;
    }

    public void setTourRadiusMeters(int tourRadiusMeters) {
        this.tourRadiusMeters = tourRadiusMeters;
    }

    public int getTourPageSize() {
        return tourPageSize;
    }

    public void setTourPageSize(int tourPageSize) {
        this.tourPageSize = tourPageSize;
    }
}
