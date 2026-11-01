package com.example.justgo.backend.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.example.justgo.backend.integration.routing.MockDrivingRouteProvider;
import com.example.justgo.backend.integration.routing.UnavailableDrivingRouteProvider;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

/**
 * 추천 서비스의 허용 시간 검증과 MOCK 출처 표시를 확인합니다.
 */
class RouteRecommendationServiceTest {

    private final RouteRecommendationService service =
            new RouteRecommendationService(new MockDrivingRouteProvider());

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
                new RouteRecommendationService(new UnavailableDrivingRouteProvider());

        RouteRecommendationResponse response = unavailableService.recommend(new RouteRecommendationRequest(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                30,
                ScenicTheme.MOUNTAIN));

        assertThat(response.mode()).isEqualTo("UNAVAILABLE");
        assertThat(response.mock()).isFalse();
        assertThat(response.routes()).isEmpty();
    }
}
