package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

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
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(12_345L, 4_501L, 18));

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
        assertThat(result.routes().getFirst().repeatedSegmentPercent()).isEqualTo(18);
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
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(4_200L, 900L, null));

        var result = provider.calculate(request(TripType.ONE_WAY, List.of(candidate(8L))));

        ArgumentCaptor<TmapRouteSearch> captor = ArgumentCaptor.forClass(TmapRouteSearch.class);
        verify(client).findRoute(captor.capture());
        TmapRouteSearch search = captor.getValue();
        assertThat(search.end().latitude()).isEqualByComparingTo("0.300000");
        assertThat(search.end().longitude()).isEqualByComparingTo("0.400000");
        assertThat(search.waypoints()).isEmpty();
        assertThat(result.routes().getFirst().routeId()).isEqualTo("TMAP-SPOT-8");
        assertThat(result.routes().getFirst().repeatedSegmentPercent()).isNull();
        assertThat(result.routes().getFirst().stops()).singleElement()
                .satisfies(stop -> {
                    assertThat(stop.type()).isEqualTo(DrivingRouteStopType.DESTINATION);
                    assertThat(stop.name()).isEqualTo("VERIFIED_SCENIC_TEST_8");
                });
    }

    /** 상위 두 후보 조합을 포함해 추천당 호출을 열 건으로 제한합니다. */
    @Test
    void evaluatesTwoSingleRoutesAndOneCombinedRoute() {
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(1_000L, 600L, null));

        var result = provider.calculate(request(TripType.ONE_WAY,
                LongStream.rangeClosed(1, 12).mapToObj(this::candidate).toList()));

        assertThat(result.routes()).extracting(route -> route.routeId())
                .containsExactly("TMAP-SPOT-1", "TMAP-SPOT-2", "TMAP-SPOTS-1-2",
                        "TMAP-SPOT-3", "TMAP-SPOT-4", "TMAP-SPOT-5", "TMAP-SPOT-6",
                        "TMAP-SPOT-7", "TMAP-SPOT-8", "TMAP-SPOT-9");
        assertThat(result.routes().get(2).stops()).extracting(stop -> stop.type())
                .containsExactly(
                        DrivingRouteStopType.WAYPOINT,
                        DrivingRouteStopType.DESTINATION);

        ArgumentCaptor<TmapRouteSearch> captor = ArgumentCaptor.forClass(TmapRouteSearch.class);
        verify(client, times(10)).findRoute(captor.capture());
        TmapRouteSearch combinedSearch = captor.getAllValues().get(2);
        assertThat(combinedSearch.waypoints()).singleElement()
                .satisfies(waypoint -> assertThat(waypoint.id()).isEqualTo("1"));
    }

    /** 두 후보 왕복은 두 경관 지점을 순서대로 경유한 뒤 출발지로 복귀하는지 확인합니다. */
    @Test
    void buildsCombinedRoundTripWithTwoScenicWaypoints() {
        when(client.findRoute(any())).thenReturn(new TmapRouteSummary(8_000L, 3_600L, 12));

        var result = provider.calculate(request(
                TripType.ROUND_TRIP,
                List.of(candidate(1L), candidate(2L))));

        ArgumentCaptor<TmapRouteSearch> captor = ArgumentCaptor.forClass(TmapRouteSearch.class);
        verify(client, times(3)).findRoute(captor.capture());
        TmapRouteSearch combinedSearch = captor.getAllValues().get(2);
        assertThat(combinedSearch.start()).isEqualTo(combinedSearch.end());
        assertThat(combinedSearch.waypoints()).extracting(TmapWaypoint::id)
                .containsExactly("1", "2");
        assertThat(result.routes().get(2).stops()).extracting(stop -> stop.type())
                .containsExactly(
                        DrivingRouteStopType.WAYPOINT,
                        DrivingRouteStopType.WAYPOINT,
                        DrivingRouteStopType.DESTINATION);
        assertThat(result.routes().get(2).repeatedSegmentPercent()).isEqualTo(12);
    }

    /** 검수 후보가 없으면 외부 호출 없이 빈 결과를 반환하는지 확인합니다. */
    @Test
    void returnsEmptyLiveResultWithoutCallingTmapWhenCandidatesAreEmpty() {
        var result = provider.calculate(request(TripType.ROUND_TRIP, List.of()));

        assertThat(result.mode()).isEqualTo(DrivingRouteMode.LIVE);
        assertThat(result.routes()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void skipsCandidatesWithoutRoutesAndKeepsCalculatedRoutes() {
        var noRoute = new TmapRouteClientException(TmapClientErrorType.NO_ROUTE, "TEST_NO_ROUTE");
        when(client.findRoute(any()))
                .thenThrow(noRoute)
                .thenReturn(new TmapRouteSummary(4_200L, 900L, null))
                .thenThrow(noRoute);

        var result = provider.calculate(request(TripType.ONE_WAY, List.of(candidate(1L), candidate(2L))));

        assertThat(result.routes()).extracting(route -> route.routeId()).containsExactly("TMAP-SPOT-2");
        verify(client, times(3)).findRoute(any());
    }

    /** 앞선 후보가 성공했다면 일일 한도 소진 시 그 결과를 버리지 않는지 확인합니다. */
    @Test
    void preservesSuccessfulRoutesWhenQuotaIsReachedMidRequest() {
        when(client.findRoute(any()))
                .thenReturn(new TmapRouteSummary(4_200L, 900L, null))
                .thenThrow(new TmapRouteClientException(
                        TmapClientErrorType.QUOTA_EXCEEDED,
                        "TEST_QUOTA_ONLY"));

        var result = provider.calculate(request(
                TripType.ONE_WAY,
                List.of(candidate(1L), candidate(2L), candidate(3L))));

        assertThat(result.mode()).isEqualTo(DrivingRouteMode.LIVE);
        assertThat(result.routes()).singleElement()
                .satisfies(route -> assertThat(route.routeId()).isEqualTo("TMAP-SPOT-1"));
        assertThat(result.notice()).contains("먼저 계산된 경로만 반환");
        verify(client, times(2)).findRoute(any());
    }

    /** 성공 경로가 하나도 없으면 호출 한도 오류를 정상 응답으로 숨기지 않는지 확인합니다. */
    @Test
    void propagatesQuotaFailureWhenNoRouteWasCalculated() {
        when(client.findRoute(any())).thenThrow(new TmapRouteClientException(
                TmapClientErrorType.QUOTA_EXCEEDED,
                "TEST_QUOTA_ONLY"));

        assertThatThrownBy(() -> provider.calculate(request(
                TripType.ONE_WAY,
                List.of(candidate(1L), candidate(2L)))))
                .isInstanceOf(TmapRouteClientException.class)
                .satisfies(exception -> assertThat(
                        ((TmapRouteClientException) exception).errorType())
                        .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
        verify(client).findRoute(any());
    }

    /** 인증 같은 전역 오류는 앞선 성공 여부와 관계없이 부분 결과로 감추지 않습니다. */
    @Test
    void propagatesNonQuotaFailureAfterSuccessfulRoute() {
        when(client.findRoute(any()))
                .thenReturn(new TmapRouteSummary(4_200L, 900L, null))
                .thenThrow(new TmapRouteClientException(
                        TmapClientErrorType.AUTHENTICATION,
                        "TEST_AUTH_ONLY"));

        assertThatThrownBy(() -> provider.calculate(request(
                TripType.ONE_WAY,
                List.of(candidate(1L), candidate(2L)))))
                .isInstanceOf(TmapRouteClientException.class)
                .satisfies(exception -> assertThat(
                        ((TmapRouteClientException) exception).errorType())
                        .isEqualTo(TmapClientErrorType.AUTHENTICATION));
        verify(client, times(2)).findRoute(any());
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
