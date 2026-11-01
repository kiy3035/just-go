package com.example.justgo.backend.route;

import java.util.List;

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
        List<RouteRecommendationStop> stops,
        String reason) {

    /** 외부 응답 생성 후 지점 순서가 바뀌지 않도록 방어적으로 복사합니다. */
    public RouteRecommendationItem {
        stops = List.copyOf(stops);
    }
}
