package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/** 실제 계산 경로의 시간 범위와 추천 순서를 검증합니다. */
class RouteRecommendationPolicyTest {

    private final RouteRecommendationPolicy policy = new RouteRecommendationPolicy();

    @Test
    void treatsEveryRequestedDurationAsAnInclusiveUpperBound() {
        for (int desired : List.of(30, 60, 120, 75)) {
            var ranked = policy.rankByDurationProximity(desired, List.of(
                    candidate("TOO_LONG", desired + 1, 500),
                    candidate("ZERO", 0, 500),
                    candidate("EXACT", desired, 2_000),
                    candidate("SHORT", 1, 1_000)));

            assertThat(ranked).extracting(DrivingRouteCandidate::routeId)
                    .containsExactly("EXACT", "SHORT");
        }
    }

    /** 목표 시간 차이와 거리로 정렬하고 최대 열 건만 반환합니다. */
    @Test
    void sortsByDurationDifferenceAndDistanceThenLimitsToTen() {
        var candidates = new ArrayList<>(List.of(
                candidate("FARTHER_DISTANCE", 57, 9_000),
                candidate("EXACT", 60, 8_000),
                candidate("THIRD", 55, 1_000),
                candidate("NEARER_DISTANCE", 57, 4_000)));
        IntStream.rangeClosed(1, 8).mapToObj(index -> candidate("OTHER_" + index, 50, index))
                .forEach(candidates::add);
        var ranked = policy.rankByDurationProximity(60, candidates);

        assertThat(ranked)
                .extracting(DrivingRouteCandidate::routeId)
                .containsExactly("EXACT", "NEARER_DISTANCE", "FARTHER_DISTANCE", "THIRD",
                        "OTHER_1", "OTHER_2", "OTHER_3", "OTHER_4", "OTHER_5", "OTHER_6");
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
