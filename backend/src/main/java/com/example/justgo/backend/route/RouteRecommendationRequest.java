package com.example.justgo.backend.route;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 요청의 외부 JSON 계약입니다.
 * 좌표는 WGS84이며 실제 사용자 위치를 서버 로그나 DB에 저장하지 않습니다.
 */
public record RouteRecommendationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @Min(10) @Max(240) Integer desiredMinutes,
        @NotNull TripType tripType,
        @NotNull ScenicTheme theme) {
}
