package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import com.example.justgo.backend.integration.routing.DrivingRouteStop;
import com.example.justgo.backend.integration.routing.DrivingRouteStopType;
import com.example.justgo.backend.integration.routing.MockDrivingRouteProvider;
import com.example.justgo.backend.integration.routing.UnavailableDrivingRouteProvider;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/**
 * 추천 서비스의 허용 시간 검증과 MOCK 출처 표시를 확인합니다.
 */
class RouteRecommendationServiceTest {

    private final SpotCandidateQuery emptySpotCandidateQuery = (theme, limit) -> List.of();
    private final RouteRecommendationService service =
            new RouteRecommendationService(new MockDrivingRouteProvider(), emptySpotCandidateQuery);

    /**
     * MOCK 응답이 실제 결과와 혼동되지 않도록 모드와 플래그를 함께 노출하는지 확인합니다.
     */
    @Test
    void returnsClearlyMarkedMockResult() {
        RouteRecommendationResponse response = service.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                60,
                TripType.ROUND_TRIP,
                ScenicTheme.RIVER));

        assertThat(response.mode()).isEqualTo("MOCK");
        assertThat(response.mock()).isTrue();
        assertThat(response.routes()).hasSize(1);
        assertThat(response.suggestions()).isEmpty();
        assertThat(response.routes().getFirst().routeId()).startsWith("MOCK-");
        assertThat(response.routes().getFirst().repeatedSegmentPercent()).isNull();
        assertThat(response.routes().getFirst().reason()).contains("실제 도로 경로");
    }

    /**
     * 5분 단위가 아닌 직접 입력 시간을 거부하는지 확인합니다.
     */
    @Test
    void rejectsUnsupportedDuration() {
        RouteRecommendationRequest request = new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                44,
                TripType.ONE_WAY,
                ScenicTheme.CITY);

        assertThatThrownBy(() -> service.recommend(request))
                .isInstanceOf(UnsupportedDrivingDurationException.class)
                .hasMessageContaining("10~240분");
    }

    /**
     * 일반 실행 환경에서는 가짜 코스를 만들지 않고 미연동 상태와 빈 목록을 반환하는지 확인합니다.
     */
    @Test
    void returnsEmptyResultWhenProviderIsUnavailable() {
        RouteRecommendationService unavailableService =
                new RouteRecommendationService(
                        new UnavailableDrivingRouteProvider(),
                        emptySpotCandidateQuery);

        RouteRecommendationResponse response = unavailableService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                30,
                TripType.ROUND_TRIP,
                ScenicTheme.MOUNTAIN));

        assertThat(response.mode()).isEqualTo("UNAVAILABLE");
        assertThat(response.mock()).isFalse();
        assertThat(response.routes()).isEmpty();
        assertThat(response.suggestions()).isEmpty();
    }

    /** 선택 테마 후보가 없으면 실제 검수 후보가 있는 다른 테마만 재검색 조건으로 제안합니다. */
    @Test
    void suggestsAvailableThemesWhenSelectedThemeHasNoCandidates() {
        SpotCandidateQuery availableThemeQuery = new SpotCandidateQuery() {
            @Override
            public List<SpotCandidateRecord> loadVerifiedCandidates(ScenicTheme theme, int limit) {
                return List.of();
            }

            @Override
            public List<ScenicTheme> loadAvailableThemes() {
                return List.of(
                        ScenicTheme.SEA,
                        ScenicTheme.MOUNTAIN,
                        ScenicTheme.LAKE,
                        ScenicTheme.RIVER,
                        ScenicTheme.CITY);
            }
        };
        RouteRecommendationService unavailableService = new RouteRecommendationService(
                new UnavailableDrivingRouteProvider(),
                availableThemeQuery);

        RouteRecommendationResponse response = unavailableService.recommend(
                new RouteRecommendationRequest(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        60,
                        TripType.ROUND_TRIP,
                        ScenicTheme.MOUNTAIN));

        assertThat(response.suggestions())
                .extracting(RouteConditionSuggestion::theme)
                .containsExactly(ScenicTheme.CITY, ScenicTheme.RIVER, ScenicTheme.LAKE);
        assertThat(response.suggestions()).allSatisfy(suggestion -> {
            assertThat(suggestion.desiredMinutes()).isEqualTo(60);
            assertThat(suggestion.tripType()).isEqualTo(TripType.ROUND_TRIP);
            assertThat(suggestion.reason()).contains("재검색");
        });
        assertThat(response.notice()).contains("다른 풍경");
    }

    /**
     * DB의 검수 완료 장소가 외부 제공자 경계에 필요한 최소 좌표로 전달되는지 확인합니다.
     */
    @Test
    void passesVerifiedSpotCandidatesToProvider() {
        SpotCandidateQuery candidateQuery = (theme, limit) -> List.of(new SpotCandidateRecord(
                7L,
                "VERIFIED_WAYPOINT_TEST_ONLY",
                ScenicTheme.LAKE,
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000")));
        AtomicReference<DrivingRouteRequest> capturedRequest = new AtomicReference<>();
        RouteRecommendationService candidateService = new RouteRecommendationService(request -> {
            capturedRequest.set(request);
            return new DrivingRouteResult(
                    DrivingRouteMode.UNAVAILABLE,
                    false,
                    List.of(),
                    "테스트용 미연동 상태");
        }, candidateQuery);

        candidateService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                90,
                TripType.ONE_WAY,
                ScenicTheme.LAKE));

        assertThat(capturedRequest.get().waypointCandidates()).hasSize(1);
        assertThat(capturedRequest.get().waypointCandidates().getFirst().spotId()).isEqualTo(7L);
        assertThat(capturedRequest.get().waypointCandidates().getFirst().name())
                .isEqualTo("VERIFIED_WAYPOINT_TEST_ONLY");
        assertThat(capturedRequest.get().tripType()).isEqualTo(TripType.ONE_WAY);
    }

    /**
     * 프리셋에 없는 직접 입력 시간도 임시 범위와 5분 단위를 만족하면 허용합니다.
     */
    @Test
    void acceptsCustomDurationInFiveMinuteSteps() {
        RouteRecommendationResponse response = service.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                75,
                TripType.ONE_WAY,
                ScenicTheme.SEA));

        assertThat(response.routes().getFirst().estimatedMinutes()).isEqualTo(75);
    }

    /** LIVE 후보에만 시간 오차 필터와 가까운 시간 우선 정렬을 적용합니다. */
    @Test
    void filtersAndRanksLiveRoutesByDesiredDuration() {
        RouteRecommendationService liveService = new RouteRecommendationService(request ->
                new DrivingRouteResult(
                        DrivingRouteMode.LIVE,
                        false,
                        List.of(
                                liveCandidate("OUTSIDE", 80, 1_000),
                                liveCandidate("THIRD", 55, 2_000),
                                liveCandidate("FIRST", 60, 3_000),
                                liveCandidate("SECOND", 63, 4_000)),
                        "LIVE 테스트 결과"),
                emptySpotCandidateQuery);

        RouteRecommendationResponse response = liveService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                60,
                TripType.ROUND_TRIP,
                ScenicTheme.RIVER));

        assertThat(response.routes())
                .extracting(RouteRecommendationItem::routeId)
                .containsExactly("FIRST", "SECOND", "THIRD");
    }

    /** 허용 오차 안의 LIVE 후보가 없으면 가짜 코스 대신 실제 후보 기반 대체 시간을 반환합니다. */
    @Test
    void returnsEmptyLiveResultWhenNoRouteMatchesDuration() {
        RouteRecommendationService liveService = new RouteRecommendationService(request ->
                new DrivingRouteResult(
                        DrivingRouteMode.LIVE,
                        false,
                        List.of(
                                liveCandidate("OUTSIDE_NEAR", 90, 1_000),
                                liveCandidate("OUTSIDE_FAR", 123, 2_000)),
                        "LIVE 테스트 결과"),
                emptySpotCandidateQuery);

        RouteRecommendationResponse response = liveService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                60,
                TripType.ONE_WAY,
                ScenicTheme.LAKE));

        assertThat(response.mode()).isEqualTo("LIVE");
        assertThat(response.routes()).isEmpty();
        assertThat(response.suggestions())
                .extracting(RouteConditionSuggestion::desiredMinutes)
                .containsExactly(90, 125);
        assertThat(response.suggestions()).allSatisfy(suggestion -> {
            assertThat(suggestion.tripType()).isEqualTo(TripType.ONE_WAY);
            assertThat(suggestion.theme()).isEqualTo(ScenicTheme.LAKE);
        });
        assertThat(response.notice()).contains("±15%", "제안된 시간");
    }

    /** Provider 지점 모델을 Android가 사용할 수 있는 API 지점 DTO로 변환합니다. */
    @Test
    void mapsNavigationStopsToApiResponse() {
        DrivingRouteCandidate routeWithStop = new DrivingRouteCandidate(
                "WITH_STOP",
                "ROUTE_STOP_MAPPING_TEST_ONLY",
                60,
                10_000,
                ScenicTheme.MOUNTAIN,
                12,
                List.of(new DrivingRouteStop(
                        1,
                        DrivingRouteStopType.DESTINATION,
                        "DESTINATION_TEST_ONLY",
                        new BigDecimal("0.300000"),
                        new BigDecimal("0.400000"))),
                "ROUTE_STOP_MAPPING_TEST_ONLY");
        RouteRecommendationService liveService = new RouteRecommendationService(request ->
                new DrivingRouteResult(
                        DrivingRouteMode.LIVE,
                        false,
                        List.of(routeWithStop),
                        "LIVE 테스트 결과"),
                emptySpotCandidateQuery);

        RouteRecommendationResponse response = liveService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                60,
                TripType.ONE_WAY,
                ScenicTheme.MOUNTAIN));

        assertThat(response.routes().getFirst().stops()).singleElement()
                .satisfies(stop -> {
                    assertThat(stop.sequence()).isEqualTo(1);
                    assertThat(stop.type()).isEqualTo("DESTINATION");
                    assertThat(stop.name()).isEqualTo("DESTINATION_TEST_ONLY");
                    assertThat(stop.latitude()).isEqualByComparingTo("0.300000");
                });
        assertThat(response.routes().getFirst().repeatedSegmentPercent()).isEqualTo(12);
    }

    /** 서비스 테스트용 LIVE 후보를 실제 위치와 무관한 값으로 생성합니다. */
    private DrivingRouteCandidate liveCandidate(String id, int minutes, long distanceMeters) {
        return new DrivingRouteCandidate(
                id,
                "LIVE_RANKING_TEST_ONLY",
                minutes,
                distanceMeters,
                ScenicTheme.RIVER,
                null,
                List.of(),
                "LIVE_RANKING_TEST_ONLY");
    }
}
