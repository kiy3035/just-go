package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간과 가까운 순서로 정렬합니다.
 * 목표 시간에서 과도하게 벗어난 경로는 추천으로 오인되지 않도록 제외합니다.
 */
final class RouteRecommendationPolicy {

    private static final int MAX_RECOMMENDATIONS = 10;
    private static final int DURATION_TOLERANCE_PERCENT = 15;

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
                .filter(candidate -> isWithinDurationTolerance(desiredMinutes, candidate))
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

    /** 목표 시간의 앞뒤 15% 안에 들어온 실제 경로만 추천 대상으로 인정합니다. */
    boolean isWithinDurationTolerance(
            int desiredMinutes,
            DrivingRouteCandidate candidate) {
        long desiredSeconds = desiredMinutes * 60L;
        long differenceSeconds = Math.abs(candidate.durationSeconds() - desiredSeconds);
        return differenceSeconds * 100L
                <= desiredSeconds * DURATION_TOLERANCE_PERCENT;
    }

    /** 형상이 없는 후보는 확인된 낮은 중복률 후보 뒤에 두되 편도끼리의 기존 순서는 유지합니다. */
    private int repeatedSegmentSortValue(DrivingRouteCandidate candidate) {
        return candidate.repeatedSegmentPercent() == null
                ? Integer.MAX_VALUE
                : candidate.repeatedSegmentPercent();
    }
}
