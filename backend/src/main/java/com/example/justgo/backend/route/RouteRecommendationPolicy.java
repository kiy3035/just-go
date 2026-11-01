package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간에 맞춰 거르고 우선순위를 정합니다.
 * 허용 오차 15%는 PoC 초기 가설이며 실제 주행 결과를 확인한 뒤 조정해야 합니다.
 */
final class RouteRecommendationPolicy {

    static final int DURATION_TOLERANCE_PERCENT = 15;
    private static final int MAX_RECOMMENDATIONS = 3;

    /**
     * 희망 시간 ±15% 후보만 남겨 시간 차이, 거리, 경로 식별자 순으로 정렬합니다.
     */
    public List<DrivingRouteCandidate> rankWithinDurationTolerance(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> isWithinDurationTolerance(
                        desiredMinutes,
                        candidate.estimatedMinutes()))
                .sorted(Comparator
                        .comparingLong((DrivingRouteCandidate candidate) ->
                                durationDifference(desiredMinutes, candidate.estimatedMinutes()))
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    /** 부동소수점 오차 없이 목표 시간 대비 15% 이내인지 비교합니다. */
    private boolean isWithinDurationTolerance(int desiredMinutes, int estimatedMinutes) {
        if (estimatedMinutes <= 0) {
            return false;
        }
        return durationDifference(desiredMinutes, estimatedMinutes) * 100L
                <= (long) desiredMinutes * DURATION_TOLERANCE_PERCENT;
    }

    /** 정수 오버플로 없이 분 단위 절대 차이를 계산합니다. */
    private long durationDifference(int desiredMinutes, int estimatedMinutes) {
        return Math.abs((long) estimatedMinutes - desiredMinutes);
    }
}
