package com.example.justgo.backend.route;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 클라이언트에 전달하는 추천 코스 요약 한 건입니다.
 */
public record RouteRecommendationItem(
        String routeId,
        String title,
        int estimatedMinutes,
        long distanceMeters,
        ScenicTheme theme,
        String reason) {
}
