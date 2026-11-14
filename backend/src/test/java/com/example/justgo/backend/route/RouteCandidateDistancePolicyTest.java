package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 희망 시간과 왕복·편도에 따른 후보 직선거리 상한을 검증합니다. */
class RouteCandidateDistancePolicyTest {

    private final RouteCandidateDistancePolicy policy = new RouteCandidateDistancePolicy();

    /** 60분 편도는 최대 평균속도 기준 100km까지 1차 후보로 허용합니다. */
    @Test
    void usesWholeDurationForOneWayCandidateDistance() {
        assertThat(policy.maxStraightLineDistanceMeters(60, TripType.ONE_WAY))
                .isEqualTo(100_000L);
    }

    /** 왕복은 같은 시간 안에 복귀해야 하므로 편도 후보의 직선거리 상한을 절반으로 둡니다. */
    @Test
    void halvesCandidateDistanceForRoundTrip() {
        assertThat(policy.maxStraightLineDistanceMeters(60, TripType.ROUND_TRIP))
                .isEqualTo(50_000L);
        assertThat(policy.maxStraightLineDistanceMeters(10, TripType.ROUND_TRIP))
                .isEqualTo(8_333L);
    }
}
