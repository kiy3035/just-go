package com.example.justgo.backend.route;

import static com.example.justgo.backend.integration.routing.DrivingRouteProvider.MAX_ROUTE_OPTIONS_PER_REQUEST;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간과 가까운 순서로 정렬합니다.
 * 희망 시간에서 10분 이내인 LIVE 후보만 반환합니다.
 */
final class RouteRecommendationPolicy {

    private static final int DURATION_TOLERANCE_MINUTES = 10;

    /**
     * 허용 범위 안의 후보를 시간 차이, 왕복 중복률, 거리, 경로 식별자 순으로 정렬합니다.
     */
    public List<DrivingRouteCandidate> rankByDurationProximity(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> durationDifference(desiredMinutes, candidate.estimatedMinutes())
                        <= DURATION_TOLERANCE_MINUTES)
                .sorted(Comparator
                        .comparingLong((DrivingRouteCandidate candidate) ->
                                durationDifference(desiredMinutes, candidate.estimatedMinutes()))
                        .thenComparingInt(this::repeatedSegmentSortValue)
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_ROUTE_OPTIONS_PER_REQUEST)
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
