package com.example.justgo.backend.integration.routing;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 경로 제공자가 계산한 코스 후보 한 건입니다.
 * 현재 MOCK 구현은 화면과 API 연결 검증용 값만 만들며 실제 도로 경로가 아닙니다.
 */
public record DrivingRouteCandidate(
        String routeId,
        String title,
        int estimatedMinutes,
        long distanceMeters,
        ScenicTheme theme,
        String reason) {
}
