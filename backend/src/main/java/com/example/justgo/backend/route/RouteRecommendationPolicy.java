package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;

/**
 * 실제 자동차 경로 후보를 희망 주행 시간에 맞춰 거르고 우선순위를 정합니다.
 * 짧은 경로와 긴 경로의 허용 비율을 분리해 과도한 초과 운전을 방지합니다.
 */
final class RouteRecommendationPolicy {

    static final int DEFAULT_ALLOWED_UNDERRUN_PERCENT = 20;
    static final int DEFAULT_ALLOWED_OVERRUN_PERCENT = 15;
    private static final int MIN_DURATION_MINUTES = 10;
    private static final int MAX_DURATION_MINUTES = 240;
    private static final int DURATION_STEP_MINUTES = 5;
    private static final int MAX_RECOMMENDATIONS = 3;
    private static final int MAX_DURATION_SUGGESTIONS = 3;
    private final int allowedUnderrunPercent;
    private final int allowedOverrunPercent;

    /** 기본 PoC 허용 범위인 -20%~+15%를 적용합니다. */
    RouteRecommendationPolicy() {
        this(DEFAULT_ALLOWED_UNDERRUN_PERCENT, DEFAULT_ALLOWED_OVERRUN_PERCENT);
    }

    /** 설정된 미달·초과 허용 비율로 정책을 구성합니다. */
    RouteRecommendationPolicy(int allowedUnderrunPercent, int allowedOverrunPercent) {
        validatePercent(allowedUnderrunPercent, "미달");
        validatePercent(allowedOverrunPercent, "초과");
        this.allowedUnderrunPercent = allowedUnderrunPercent;
        this.allowedOverrunPercent = allowedOverrunPercent;
    }

    /**
     * 설정된 시간 범위 후보만 남겨 시간 차이, 왕복 중복률, 거리, 경로 식별자 순으로 정렬합니다.
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
                        .thenComparingInt(this::repeatedSegmentSortValue)
                        .thenComparingLong(DrivingRouteCandidate::distanceMeters)
                        .thenComparing(DrivingRouteCandidate::routeId))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    /**
     * 허용 오차 밖의 실제 계산 후보 시간을 5분 단위로 맞춰 가까운 대체 시간부터 제안합니다.
     * 지원 범위를 벗어나거나 같은 시간으로 겹치는 값은 제외합니다.
     */
    public List<Integer> suggestAlternativeDurations(
            int desiredMinutes,
            List<DrivingRouteCandidate> candidates) {
        return candidates.stream()
                .mapToInt(DrivingRouteCandidate::estimatedMinutes)
                .filter(minutes -> minutes >= MIN_DURATION_MINUTES
                        && minutes <= MAX_DURATION_MINUTES)
                .map(this::roundToSupportedDuration)
                .filter(minutes -> minutes != desiredMinutes)
                .boxed()
                .distinct()
                .sorted(Comparator
                        .comparingLong((Integer minutes) -> durationDifference(desiredMinutes, minutes))
                        .thenComparingInt(Integer::intValue))
                .limit(MAX_DURATION_SUGGESTIONS)
                .toList();
    }

    /** 사용자 안내에 표시할 짧은 경로 허용 비율을 반환합니다. */
    int allowedUnderrunPercent() {
        return allowedUnderrunPercent;
    }

    /** 사용자 안내에 표시할 긴 경로 허용 비율을 반환합니다. */
    int allowedOverrunPercent() {
        return allowedOverrunPercent;
    }

    /** 부동소수점 오차 없이 서로 다른 하한·상한 범위에 포함되는지 비교합니다. */
    private boolean isWithinDurationTolerance(int desiredMinutes, int estimatedMinutes) {
        if (estimatedMinutes <= 0) {
            return false;
        }
        long scaledEstimatedMinutes = (long) estimatedMinutes * 100L;
        long lowerBound = (long) desiredMinutes * (100 - allowedUnderrunPercent);
        long upperBound = (long) desiredMinutes * (100 + allowedOverrunPercent);
        return scaledEstimatedMinutes >= lowerBound && scaledEstimatedMinutes <= upperBound;
    }

    /** 직접 생성 시에도 잘못된 비율이 조용히 적용되지 않도록 방어합니다. */
    private void validatePercent(int percent, String boundaryName) {
        if (percent < 0 || percent > 50) {
            throw new IllegalArgumentException(
                    "추천 시간 " + boundaryName + " 허용 비율은 0~50%여야 합니다.");
        }
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

    /** 가장 가까운 지원 시간인 5분 단위로 반올림합니다. */
    private int roundToSupportedDuration(int estimatedMinutes) {
        return ((estimatedMinutes + DURATION_STEP_MINUTES / 2) / DURATION_STEP_MINUTES)
                * DURATION_STEP_MINUTES;
    }
}
