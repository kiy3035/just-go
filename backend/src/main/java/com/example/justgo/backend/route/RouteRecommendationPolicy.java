package com.example.justgo.backend.route;

import static com.example.justgo.backend.integration.routing.DrivingRouteProvider.MAX_ROUTE_OPTIONS_PER_REQUEST;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간과 가까운 순서로 정렬합니다.
 * 희망 시간 이하인 LIVE 후보만 반환합니다.
 */
final class RouteRecommendationPolicy {

    /**
     * 희망 시간에 가까운 순으로 정렬하고 음식점·카페는 같은 시간이면 도로거리를 우선합니다.
     */
    public List<DrivingRouteCandidate> rankByDurationProximity(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate.estimatedMinutes() > 0
                        && candidate.estimatedMinutes() <= desiredMinutes)
                .sorted(Comparator
                        .comparingInt(DrivingRouteCandidate::estimatedMinutes).reversed()
                        .thenComparingLong(candidate ->
                                candidate.theme().isDining() ? candidate.distanceMeters() : 0L)
                        .thenComparingInt(this::repeatedSegmentSortValue)
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_ROUTE_OPTIONS_PER_REQUEST)
                .toList();
    }

    /** 형상이 없는 후보는 확인된 낮은 중복률 후보 뒤에 두되 편도끼리의 기존 순서는 유지합니다. */
    private int repeatedSegmentSortValue(DrivingRouteCandidate candidate) {
        return candidate.repeatedSegmentPercent() == null
                ? Integer.MAX_VALUE
                : candidate.repeatedSegmentPercent();
    }
}
