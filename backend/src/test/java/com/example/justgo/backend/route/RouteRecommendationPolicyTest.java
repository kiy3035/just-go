package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/** 목표 시간 허용범위와 안정적인 추천 순서를 검증합니다. */
class RouteRecommendationPolicyTest {

    private final RouteRecommendationPolicy policy = new RouteRecommendationPolicy();

    /** 60분 목표의 15% 경계는 포함하고 경계 밖 경로는 제외합니다. */
    @Test
    void keepsOnlyCandidatesWithinDurationTolerance() {
        var ranked = policy.rankByDurationProximity(60, List.of(
                candidate("LOWER_OUTSIDE", 50, 1_000),
                candidate("LOWER_BOUNDARY", 51, 2_000),
                candidate("UPPER_BOUNDARY", 69, 3_000),
                candidate("UPPER_OUTSIDE", 70, 4_000)));

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("LOWER_BOUNDARY", "UPPER_BOUNDARY");
    }

    /** 목표 시간의 앞뒤를 모두 비교하고 최대 열 건만 반환한다. */
    @Test
    void sortsByDurationDifferenceAndDistanceThenLimitsToTen() {
        var candidates = new java.util.ArrayList<>(List.of(
                candidate("ABOVE_TARGET", 62, 9_000),
                candidate("EXACT", 60, 8_000),
                candidate("FIVE_MINUTES_AWAY", 55, 1_000),
                candidate("BELOW_TARGET", 58, 4_000)));
        for (int index = 0; index < 10; index++) {
            candidates.add(candidate("EXACT_" + index, 60, 10_000L + index));
        }
        var ranked = policy.rankByDurationProximity(60, candidates);

        assertThat(ranked).hasSize(10)
                .extracting(DrivingRouteCandidate::routeId)
                .startsWith("EXACT", "EXACT_0", "EXACT_1", "EXACT_2");
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
