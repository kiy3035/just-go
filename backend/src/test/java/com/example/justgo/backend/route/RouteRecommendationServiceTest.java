package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
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
                ScenicTheme.RIVER));

        assertThat(response.mode()).isEqualTo("MOCK");
        assertThat(response.mock()).isTrue();
        assertThat(response.routes()).hasSize(1);
        assertThat(response.routes().getFirst().routeId()).startsWith("MOCK-");
        assertThat(response.routes().getFirst().reason()).contains("실제 도로 경로");
    }

    /**
     * 범위 안의 임의 숫자가 아니라 제품에서 확정한 네 가지 시간만 허용하는지 확인합니다.
     */
    @Test
    void rejectsUnsupportedDuration() {
        RouteRecommendationRequest request = new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                45,
                ScenicTheme.CITY);

        assertThatThrownBy(() -> service.recommend(request))
                .isInstanceOf(UnsupportedDrivingDurationException.class)
                .hasMessageContaining("30, 60, 90, 120");
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
                ScenicTheme.MOUNTAIN));

        assertThat(response.mode()).isEqualTo("UNAVAILABLE");
        assertThat(response.mock()).isFalse();
        assertThat(response.routes()).isEmpty();
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
                ScenicTheme.LAKE));

        assertThat(capturedRequest.get().waypointCandidates()).hasSize(1);
        assertThat(capturedRequest.get().waypointCandidates().getFirst().spotId()).isEqualTo(7L);
        assertThat(capturedRequest.get().waypointCandidates().getFirst().name())
                .isEqualTo("VERIFIED_WAYPOINT_TEST_ONLY");
    }
}
