package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/** 실제 계산 경로를 숨기지 않는 안정적인 추천 순서를 검증합니다. */
class RouteRecommendationPolicyTest {

    private final RouteRecommendationPolicy policy = new RouteRecommendationPolicy();

    /** 희망 시간에서 멀리 벗어난 경로도 시간 차이가 가까운 순으로 모두 유지합니다. */
    @Test
    void keepsCalculatedCandidatesOutsideFormerDurationTolerance() {
        var ranked = policy.rankByDurationProximity(60, List.of(
                candidate("LONG", 123, 2_000),
                candidate("NEAR", 90, 1_000)));

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("NEAR", "LONG");
    }

    /** 목표 시간의 앞뒤를 모두 비교하고 최대 열 건만 반환한다. */
    @Test
    void sortsByDurationDifferenceAndDistanceThenLimitsToTen() {
        var candidates = new java.util.ArrayList<>(List.of(
                candidate("ABOVE_TARGET", 62, 9_000),
                candidate("EXACT", 60, 8_000),
                candidate("FIVE_MINUTES_AWAY", 55, 1_000),
                candidate("BELOW_TARGET", 58, 4_000)));
        for (int minutes = 70; minutes < 80; minutes++) {
            candidates.add(candidate("SLOW_" + minutes, minutes, 1_000));
        }
        var ranked = policy.rankByDurationProximity(60, candidates);

        assertThat(ranked).hasSize(10)
                .extracting(DrivingRouteCandidate::routeId)
                .startsWith("EXACT", "BELOW_TARGET", "ABOVE_TARGET", "FIVE_MINUTES_AWAY");
    }

    /** 시간 차이가 같으면 중복 주행 비율이 낮은 왕복 후보를 거리보다 먼저 선택합니다. */
    @Test
    void prefersLowerRepeatedSegmentPercentForEqualDurationDifference() {
        var ranked = policy.rankByDurationProximity(60, List.of(
                candidate("UNKNOWN_SHAPE", 60, 1_000, null),
                candidate("HIGH_REPEAT", 60, 2_000, 45),
                candidate("LOW_REPEAT", 60, 9_000, 10)));

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("LOW_REPEAT", "HIGH_REPEAT", "UNKNOWN_SHAPE");
    }

    /** 도시 행사 우선순위 안에서도 목표 시간과의 차이로 정렬한다. */
    @Test
    void ranksAvailableEventsBeforeOrdinaryCityDestinations() {
        var ranked = policy.rankByDurationProximity(60, List.of(
                candidate("ATTRACTION", 60, 2_000),
                candidate("EVENT_66", 66, 3_000),
                candidate("EVENT_58", 58, 4_000)), Set.of("EVENT_66", "EVENT_58"));

        assertThat(ranked).extracting(DrivingRouteCandidate::routeId)
                .containsExactly("EVENT_58", "EVENT_66", "ATTRACTION");
    }

    /** 테스트 전용 후보를 명백한 가짜 값으로 생성합니다. */
    private DrivingRouteCandidate candidate(String id, int minutes, long distanceMeters) {
        return candidate(id, minutes, distanceMeters, null);
    }

    /** 중복률을 지정할 수 있는 테스트 전용 후보를 생성합니다. */
    private DrivingRouteCandidate candidate(
            String id,
            int minutes,
            long distanceMeters,
            Integer repeatedSegmentPercent) {
        return new DrivingRouteCandidate(
                id,
                "RANKING_TEST_ONLY",
                minutes,
                distanceMeters,
                ScenicTheme.RIVER,
                repeatedSegmentPercent,
                List.of(),
                "RANKING_TEST_ONLY");
    }
}
