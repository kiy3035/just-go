package com.example.justgo.backend.route;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 추천 시간 허용 범위를 환경별 설정으로 바인딩합니다.
 * 운전 시간이 선택값보다 길어지는 경우를 더 보수적으로 제한하도록 상·하한을 분리합니다.
 */
@Validated
@ConfigurationProperties(prefix = "justgo.recommendation")
public class RouteRecommendationProperties {

    @Min(0)
    @Max(50)
    private int allowedUnderrunPercent = 20;

    @Min(0)
    @Max(50)
    private int allowedOverrunPercent = 15;

    public int getAllowedUnderrunPercent() {
        return allowedUnderrunPercent;
    }

    public void setAllowedUnderrunPercent(int allowedUnderrunPercent) {
        this.allowedUnderrunPercent = allowedUnderrunPercent;
    }

    public int getAllowedOverrunPercent() {
        return allowedOverrunPercent;
    }

    public void setAllowedOverrunPercent(int allowedOverrunPercent) {
        this.allowedOverrunPercent = allowedOverrunPercent;
    }
}
