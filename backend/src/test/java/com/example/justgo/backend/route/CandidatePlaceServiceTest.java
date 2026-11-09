package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import org.junit.jupiter.api.Test;

/** 넓은 후보 조회 후 선택한 한 곳만 경로 제공자에 전달하는 흐름을 검증합니다. */
class CandidatePlaceServiceTest {

    @Test
    void selectedTokenCalculatesOnlyTheChosenCandidate() {
        SpotCandidateRecord candidate = new SpotCandidateRecord(
                -7L,
                "SELECTED_PUBLIC_PLACE_TEST_ONLY",
                ScenicTheme.PARK,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                "PUBLIC_PARK");
        SpotCandidateQuery query = (theme, limit) -> List.of(candidate);
        CandidatePlaceSelectionStore store = new CandidatePlaceSelectionStore();
        CandidatePlaceService candidateService = new CandidatePlaceService(query, store);
        CandidatePlaceListResponse listed = candidateService.findCandidates(
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                60,
                TripType.ONE_WAY,
                ScenicTheme.PARK,
                20);

        AtomicReference<DrivingRouteRequest> captured = new AtomicReference<>();
        RouteRecommendationService routeService = new RouteRecommendationService(request -> {
            captured.set(request);
            return new DrivingRouteResult(
                    DrivingRouteMode.UNAVAILABLE,
                    false,
                    List.of(),
                    "SELECTION_TEST_ONLY");
        }, (theme, limit) -> {
            throw new AssertionError("선택 토큰이 있으면 자동 후보 조회를 호출하면 안 됩니다.");
        }, store);

        routeService.recommend(new RouteRecommendationRequest(
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                60,
                TripType.ONE_WAY,
                ScenicTheme.PARK,
                listed.candidates().getFirst().candidateToken()));

        assertThat(listed.count()).isEqualTo(1);
        assertThat(listed.tokenExpiresInMinutes()).isEqualTo(30);
        assertThat(listed.partial()).isFalse();
        assertThat(listed.unavailableSources()).isEmpty();
        assertThat(captured.get().waypointCandidates()).singleElement().satisfies(waypoint -> {
            assertThat(waypoint.spotId()).isEqualTo(-7L);
            assertThat(waypoint.name()).isEqualTo("SELECTED_PUBLIC_PLACE_TEST_ONLY");
        });
    }
}
