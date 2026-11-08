package com.example.justgo.backend.route;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 추천 요청의 외부 JSON 계약입니다.
 * 좌표는 WGS84이며 실제 사용자 위치를 서버 로그나 DB에 저장하지 않습니다.
 */
public record RouteRecommendationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @Min(10) @Max(240) Integer desiredMinutes,
        @NotNull TripType tripType,
        @NotNull ScenicTheme theme,
        @Pattern(
                regexp = "^[0-9a-f-]{36}$",
                message = "후보 토큰 형식을 확인해 주세요.")
        String candidateToken) {

    /** 후보를 먼저 고르지 않는 기존 요청 계약도 그대로 지원합니다. */
    public RouteRecommendationRequest(
            BigDecimal latitude,
            BigDecimal longitude,
            Integer desiredMinutes,
            TripType tripType,
            ScenicTheme theme) {
        this(latitude, longitude, desiredMinutes, tripType, theme, null);
    }
}
