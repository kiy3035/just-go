package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간과 가까운 순서로 정렬합니다.
 * 계산이 끝난 LIVE 후보는 시간 차이가 크더라도 숨기지 않고 사용자가 비교하게 합니다.
 */
final class RouteRecommendationPolicy {

    private static final int MAX_RECOMMENDATIONS = 10;

    /**
     * 기본 순위는 목표와의 실제 초 단위 차이이며 동률일 때만 중복률과 거리를 비교한다.
     */
    public List<DrivingRouteCandidate> rankByDurationProximity(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return rankByDurationProximity(desiredMinutes, candidates, java.util.Set.of());
    }

    public List<DrivingRouteCandidate> rankByDurationProximity(int desiredMinutes,
            List<DrivingRouteCandidate> candidates, java.util.Set<String> eventRouteIds) {
        // 도시에서는 관람 가능한 행사 그룹을 먼저 배치하고 각 그룹 안에서 시간을 비교한다.
        return candidates.stream()
                .sorted(Comparator
                        .comparing((DrivingRouteCandidate candidate) -> !eventRouteIds.contains(candidate.routeId()))
                        .thenComparingLong(candidate ->
                                Math.abs(candidate.durationSeconds() - desiredMinutes * 60L))
                        .thenComparingInt(this::repeatedSegmentSortValue)
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    /** 형상이 없는 후보는 확인된 낮은 중복률 후보 뒤에 두되 편도끼리의 기존 순서는 유지합니다. */
    private int repeatedSegmentSortValue(DrivingRouteCandidate candidate) {
        return candidate.repeatedSegmentPercent() == null
                ? Integer.MAX_VALUE
                : candidate.repeatedSegmentPercent();
    }
}
