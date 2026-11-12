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
        Integer repeatedSegmentPercent,
        List<RouteRecommendationStop> stops,
        String reason,
        long durationSeconds,
        List<Long> legSeconds,
        EventDetails event) {

    /** 외부 응답 생성 후 지점 순서가 바뀌지 않도록 방어적으로 복사합니다. */
    public RouteRecommendationItem {
        stops = List.copyOf(stops);
        legSeconds = List.copyOf(legSeconds);
    }

    /** 행사 정보는 검증된 운영 시간에 도착 가능한 도시 경로에만 제공한다. */
    public record EventDetails(String name, String venue, String opensAt, String closesAt, String arrivesAt) {}
}
