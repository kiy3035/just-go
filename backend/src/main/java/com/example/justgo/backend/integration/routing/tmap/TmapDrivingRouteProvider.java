package com.example.justgo.backend.integration.routing.tmap;

import java.util.ArrayList;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteProvider;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import com.example.justgo.backend.integration.routing.DrivingRouteStop;
import com.example.justgo.backend.integration.routing.DrivingRouteStopType;
import com.example.justgo.backend.integration.routing.DrivingRouteWaypointCandidate;
import com.example.justgo.backend.route.TripType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 검수된 경관 후보를 TMAP 자동차 경로로 계산하는 첫 PoC 제공자입니다.
 * 무료 호출량 보호를 위해 한 추천 요청에서 최대 3개 후보만 평가합니다.
 */
@Component
@Profile("tmap & !mock")
public class TmapDrivingRouteProvider implements DrivingRouteProvider {

    private static final int MAX_EVALUATED_CANDIDATES = 3;

    private final TmapRouteClient tmapRouteClient;

    public TmapDrivingRouteProvider(TmapRouteClient tmapRouteClient) {
        this.tmapRouteClient = tmapRouteClient;
    }

    /**
     * 왕복은 후보를 경유해 출발지로 복귀하고, 편도는 후보 자체를 도착지로 사용합니다.
     */
    @Override
    public DrivingRouteResult calculate(DrivingRouteRequest request) {
        if (request.waypointCandidates().isEmpty()) {
            return new DrivingRouteResult(
                    DrivingRouteMode.UNAVAILABLE,
                    false,
                    List.of(),
                    "검수 완료된 경관 장소 후보가 없습니다.");
        }

        TmapCoordinate origin = new TmapCoordinate(request.latitude(), request.longitude());
        List<DrivingRouteCandidate> routes = new ArrayList<>();
        request.waypointCandidates().stream()
                .limit(MAX_EVALUATED_CANDIDATES)
                .forEach(candidate -> routes.add(calculateCandidate(request, origin, candidate)));

        return new DrivingRouteResult(
                DrivingRouteMode.LIVE,
                false,
                routes,
                "TMAP 자동차 경로 결과입니다. 교통 상황과 내비 재탐색에 따라 달라질 수 있습니다.");
    }

    /**
     * 후보 한 건을 주행 방식에 맞는 TMAP 검색 조건으로 변환하고 결과 단위를 정규화합니다.
     */
    private DrivingRouteCandidate calculateCandidate(
            DrivingRouteRequest request,
            TmapCoordinate origin,
            DrivingRouteWaypointCandidate candidate) {
        TmapCoordinate candidateCoordinate = new TmapCoordinate(
                candidate.latitude(),
                candidate.longitude());
        TmapRouteSearch search = request.tripType() == TripType.ROUND_TRIP
                ? new TmapRouteSearch(
                        origin,
                        origin,
                        List.of(new TmapWaypoint(
                                Long.toString(candidate.spotId()),
                                candidate.name(),
                                candidateCoordinate)))
                : new TmapRouteSearch(origin, candidateCoordinate, List.of());
        TmapRouteSummary summary = tmapRouteClient.findRoute(search);

        return new DrivingRouteCandidate(
                "TMAP-SPOT-" + candidate.spotId(),
                candidate.name() + " 드라이브",
                toWholeMinutes(summary.durationSeconds()),
                summary.distanceMeters(),
                request.theme(),
                request.tripType() == TripType.ROUND_TRIP
                        ? summary.repeatedSegmentPercent()
                        : null,
                buildStops(request, candidate),
                "검수된 경관 후보의 TMAP 자동차 경로 시간·거리 결과입니다.");
    }

    /**
     * 왕복은 경관 후보를 경유한 뒤 출발지로 돌아가고, 편도는 후보에서 종료합니다.
     */
    private List<DrivingRouteStop> buildStops(
            DrivingRouteRequest request,
            DrivingRouteWaypointCandidate candidate) {
        DrivingRouteStop scenicStop = new DrivingRouteStop(
                1,
                request.tripType() == TripType.ROUND_TRIP
                        ? DrivingRouteStopType.WAYPOINT
                        : DrivingRouteStopType.DESTINATION,
                candidate.name(),
                candidate.latitude(),
                candidate.longitude());
        if (request.tripType() == TripType.ONE_WAY) {
            return List.of(scenicStop);
        }
        DrivingRouteStop returnToOrigin = new DrivingRouteStop(
                2,
                DrivingRouteStopType.DESTINATION,
                "출발지 복귀",
                request.latitude(),
                request.longitude());
        return List.of(scenicStop, returnToOrigin);
    }

    /** 초 단위 시간을 부족하게 표시하지 않도록 올림한 분 단위로 변환합니다. */
    private int toWholeMinutes(long durationSeconds) {
        return Math.toIntExact((durationSeconds + 59L) / 60L);
    }
}
