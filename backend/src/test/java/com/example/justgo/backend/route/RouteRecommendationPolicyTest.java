package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/** 희망 시간 허용 오차와 안정적인 추천 순서를 검증합니다. */
class RouteRecommendationPolicyTest {

    private final RouteRecommendationPolicy policy = new RouteRecommendationPolicy();

    /** 60분 기준 경계값 51~69분만 허용되는지 확인합니다. */
    @Test
    void keepsCandidatesWithinFifteenPercentDurationTolerance() {
        var ranked = policy.rankWithinDurationTolerance(60, List.of(
                candidate("TOO_SHORT", 50, 1_000),
                candidate("LOWER_BOUND", 51, 2_000),
                candidate("UPPER_BOUND", 69, 3_000),
                candidate("TOO_LONG", 70, 4_000)));

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("LOWER_BOUND", "UPPER_BOUND");
    }

    /** 목표 시간 차이와 거리로 정렬한 뒤 최대 세 건만 반환하는지 확인합니다. */
    @Test
    void sortsByDurationDifferenceAndDistanceThenLimitsToThree() {
        var ranked = policy.rankWithinDurationTolerance(60, List.of(
                candidate("FARTHER_DISTANCE", 63, 9_000),
                candidate("EXACT", 60, 8_000),
                candidate("THIRD", 65, 1_000),
                candidate("NEARER_DISTANCE", 63, 4_000)));

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("EXACT", "NEARER_DISTANCE", "FARTHER_DISTANCE");
    }

    /** 실제 후보 시간을 지원 범위의 5분 단위로 중복 없이 제안하는지 확인합니다. */
    @Test
    void suggestsNearestSupportedDurationsFromCalculatedCandidates() {
        var suggestions = policy.suggestAlternativeDurations(60, List.of(
                candidate("NEAR_90_A", 88, 1_000),
                candidate("NEAR_90_B", 91, 2_000),
                candidate("NEAR_125", 123, 3_000),
                candidate("SAME", 60, 4_000),
                candidate("OUT_OF_RANGE", 241, 5_000)));

        assertThat(suggestions).containsExactly(90, 125);
    }

    /** 테스트 전용 후보를 명백한 가짜 값으로 생성합니다. */
    private DrivingRouteCandidate candidate(String id, int minutes, long distanceMeters) {
        return new DrivingRouteCandidate(
                id,
                "RANKING_TEST_ONLY",
                minutes,
                distanceMeters,
                ScenicTheme.RIVER,
                List.of(),
                "RANKING_TEST_ONLY");
    }
}
