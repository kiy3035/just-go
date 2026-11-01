package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteStopType;
import com.example.justgo.backend.integration.routing.DrivingRouteWaypointCandidate;
import com.example.justgo.backend.route.TripType;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 실제 TMAP 호출 없이 왕복·편도 경로 구성과 결과 변환을 검증합니다. */
class TmapDrivingRouteProviderTest {

    private final TmapRouteClient client = mock(TmapRouteClient.class);
    private final TmapDrivingRouteProvider provider = new TmapDrivingRouteProvider(client);

    /** 왕복은 경관 후보를 경유하고 출발 좌표로 복귀하는지 확인합니다. */
    @Test
    void buildsRoundTripThroughScenicCandidate() {
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(12_345L, 4_501L));

        var result = provider.calculate(request(TripType.ROUND_TRIP, List.of(candidate(7L))));

        ArgumentCaptor<TmapRouteSearch> captor = ArgumentCaptor.forClass(TmapRouteSearch.class);
        verify(client).findRoute(captor.capture());
        TmapRouteSearch search = captor.getValue();
        assertThat(search.start()).isEqualTo(search.end());
        assertThat(search.waypoints()).hasSize(1);
        assertThat(search.waypoints().getFirst().id()).isEqualTo("7");
        assertThat(result.mode()).isEqualTo(DrivingRouteMode.LIVE);
        assertThat(result.routes().getFirst().estimatedMinutes()).isEqualTo(76);
        assertThat(result.routes().getFirst().distanceMeters()).isEqualTo(12_345L);
        assertThat(result.routes().getFirst().stops()).hasSize(2);
        assertThat(result.routes().getFirst().stops().getFirst().type())
                .isEqualTo(DrivingRouteStopType.WAYPOINT);
        assertThat(result.routes().getFirst().stops().get(1).type())
                .isEqualTo(DrivingRouteStopType.DESTINATION);
        assertThat(result.routes().getFirst().stops().get(1).latitude())
                .isEqualByComparingTo("0.100000");
    }

    /** 편도는 경관 후보를 도착점으로 사용하고 경유지를 비우는지 확인합니다. */
    @Test
    void buildsOneWayTripEndingAtScenicCandidate() {
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(4_200L, 900L));

        var result = provider.calculate(request(TripType.ONE_WAY, List.of(candidate(8L))));

        ArgumentCaptor<TmapRouteSearch> captor = ArgumentCaptor.forClass(TmapRouteSearch.class);
        verify(client).findRoute(captor.capture());
        TmapRouteSearch search = captor.getValue();
        assertThat(search.end().latitude()).isEqualByComparingTo("0.300000");
        assertThat(search.end().longitude()).isEqualByComparingTo("0.400000");
        assertThat(search.waypoints()).isEmpty();
        assertThat(result.routes().getFirst().routeId()).isEqualTo("TMAP-SPOT-8");
        assertThat(result.routes().getFirst().stops()).singleElement()
                .satisfies(stop -> {
                    assertThat(stop.type()).isEqualTo(DrivingRouteStopType.DESTINATION);
                    assertThat(stop.name()).isEqualTo("VERIFIED_SCENIC_TEST_8");
                });
    }

    /** 무료 쿼터 보호를 위해 한 요청에서 앞의 후보 세 건만 평가하는지 확인합니다. */
    @Test
    void evaluatesAtMostThreeCandidatesPerRequest() {
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(1_000L, 600L));

        var result = provider.calculate(request(TripType.ONE_WAY, List.of(
                candidate(1L),
                candidate(2L),
                candidate(3L),
                candidate(4L))));

        assertThat(result.routes()).hasSize(3);
        verify(client, times(3)).findRoute(any());
    }

    /** 검수 후보가 없으면 외부 호출 없이 빈 결과를 반환하는지 확인합니다. */
    @Test
    void returnsUnavailableWithoutCallingTmapWhenCandidatesAreEmpty() {
        var result = provider.calculate(request(TripType.ROUND_TRIP, List.of()));

        assertThat(result.mode()).isEqualTo(DrivingRouteMode.UNAVAILABLE);
        assertThat(result.routes()).isEmpty();
        verifyNoInteractions(client);
    }

    private DrivingRouteRequest request(
            TripType tripType,
            List<DrivingRouteWaypointCandidate> candidates) {
        return new DrivingRouteRequest(
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000"),
                75,
                tripType,
                ScenicTheme.RIVER,
                candidates);
    }

    private DrivingRouteWaypointCandidate candidate(long id) {
        return new DrivingRouteWaypointCandidate(
                id,
                "VERIFIED_SCENIC_TEST_" + id,
                new BigDecimal("0.300000"),
                new BigDecimal("0.400000"));
    }
}
