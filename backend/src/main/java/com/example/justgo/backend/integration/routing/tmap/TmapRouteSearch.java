package com.example.justgo.backend.integration.routing.tmap;

import java.util.List;

/**
 * TMAP 다중 경유지 30 API에 전달할 명시적인 출발지·도착지·경유지 조건입니다.
 * 왕복 여부는 이 모델이 자동 결정하지 않고 상위 추천 정책이 좌표를 구성하도록 둡니다.
 */
public record TmapRouteSearch(
        TmapCoordinate start,
        TmapCoordinate end,
        List<TmapWaypoint> waypoints) {

    private static final int MAX_WAYPOINTS = 30;

    /**
     * 공식 경유지 30 한도와 필수 좌표를 외부 호출 전에 검증합니다.
     */
    public TmapRouteSearch {
        if (start == null || end == null) {
            throw new IllegalArgumentException("TMAP 출발지와 도착지가 모두 필요합니다.");
        }
        if (waypoints == null || waypoints.isEmpty() || waypoints.size() > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("TMAP 경유지는 1개 이상 30개 이하여야 합니다.");
        }
        waypoints = List.copyOf(waypoints);
    }
}
