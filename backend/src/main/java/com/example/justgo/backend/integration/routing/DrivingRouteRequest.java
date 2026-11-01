package com.example.justgo.backend.integration.routing;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 외부 경로 제공자에 전달할 정규화된 검색 조건입니다.
 * 좌표는 WGS84 위도·경도, 시간은 분 단위를 사용합니다.
 */
public record DrivingRouteRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        int desiredMinutes,
        ScenicTheme theme) {
}
