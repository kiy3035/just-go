package com.example.justgo.backend.route;

/**
 * 희망 시간으로 도달 가능한 경관 후보의 보수적인 직선거리 상한을 계산합니다.
 * 실제 예상 시간은 외부 자동차 경로 결과로 다시 검증하며 이 값은 호출 전 후보 축소에만 사용합니다.
 */
final class RouteCandidateDistancePolicy {

    private static final long MAX_CANDIDATE_SPEED_METERS_PER_HOUR = 100_000L;
    private static final int MINUTES_PER_HOUR = 60;

    /** 왕복은 출발지 복귀 시간을 고려해 편도 후보 거리 상한을 절반으로 계산합니다. */
    long maxStraightLineDistanceMeters(int desiredMinutes, TripType tripType) {
        if (desiredMinutes <= 0 || tripType == null) {
            throw new IllegalArgumentException("희망 시간과 주행 방식이 필요합니다.");
        }
        long totalTravelUpperBound = desiredMinutes
                * MAX_CANDIDATE_SPEED_METERS_PER_HOUR
                / MINUTES_PER_HOUR;
        return tripType == TripType.ROUND_TRIP
                ? totalTravelUpperBound / 2L
                : totalTravelUpperBound;
    }
}
