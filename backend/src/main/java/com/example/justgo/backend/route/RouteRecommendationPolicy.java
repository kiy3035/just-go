package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간과 가까운 순서로 정렬합니다.
 * 계산이 끝난 LIVE 후보는 시간 차이가 크더라도 숨기지 않고 사용자가 비교하게 합니다.
 */
final class RouteRecommendationPolicy {

    private static final int MAX_RECOMMENDATIONS = 3;

    /**
     * 모든 실제 계산 후보를 시간 차이, 왕복 중복률, 거리, 경로 식별자 순으로 정렬합니다.
     */
    public List<DrivingRouteCandidate> rankByDurationProximity(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingLong((DrivingRouteCandidate candidate) ->
                                durationDifference(desiredMinutes, candidate.estimatedMinutes()))
                        .thenComparingInt(this::repeatedSegmentSortValue)
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    /** 정수 오버플로 없이 분 단위 절대 차이를 계산합니다. */
    private long durationDifference(int desiredMinutes, int estimatedMinutes) {
        return Math.abs((long) estimatedMinutes - desiredMinutes);
    }

    /** 형상이 없는 후보는 확인된 낮은 중복률 후보 뒤에 두되 편도끼리의 기존 순서는 유지합니다. */
    private int repeatedSegmentSortValue(DrivingRouteCandidate candidate) {
        return candidate.repeatedSegmentPercent() == null
                ? Integer.MAX_VALUE
                : candidate.repeatedSegmentPercent();
    }
}
