package com.example.justgo.backend.integration.routing.tmap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
 * 기존 장소 토큰 경로와 새 추천 목록용 목적지별 계산을 지원합니다.
 */
@Component
@Profile("tmap & !mock")
public class TmapDrivingRouteProvider implements DrivingRouteProvider {

    private static final int MAX_EVALUATED_ROUTE_OPTIONS = 3;
    private static final int MAX_SCENIC_STOPS_PER_ROUTE = 2;

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
                    DrivingRouteMode.LIVE,
                    false,
                    List.of(),
                    "검수 완료된 경관 장소 후보가 없습니다.");
        }

        TmapCoordinate origin = new TmapCoordinate(request.latitude(), request.longitude());
        List<DrivingRouteCandidate> routes = new ArrayList<>();
        boolean quotaReachedAfterSuccess = calculateOptions(request, origin, routes);

        return new DrivingRouteResult(
                DrivingRouteMode.LIVE,
                false,
                routes,
                routes.isEmpty() ? "자동차 경로를 찾지 못했습니다." : quotaReachedAfterSuccess
                        ? "일일 외부 경로 호출 한도에 도달해 먼저 계산된 경로만 반환합니다."
                        : "TMAP 자동차 경로 결과입니다. 교통 상황과 내비 재탐색에 따라 달라질 수 있습니다.");
    }

    /** 추천 목록용 조회에서는 조합 없이 목적지별 편도 또는 왕복 경로를 계산한다. */
    @Override
    public DrivingRouteResult calculateRanked(DrivingRouteRequest request) {
        TmapCoordinate origin = new TmapCoordinate(request.latitude(), request.longitude());
        List<DrivingRouteCandidate> routes = new ArrayList<>();
        boolean quotaReached = false;
        for (DrivingRouteWaypointCandidate candidate : request.waypointCandidates()) {
            try {
                routes.add(calculateDestination(request, origin, candidate));
            } catch (TmapRouteClientException exception) {
                if (isUnusableRoute(exception)) continue;
                if (exception.errorType() != TmapClientErrorType.QUOTA_EXCEEDED) {
                    throw exception;
                }
                quotaReached = true;
                break;
            }
        }
        return new DrivingRouteResult(DrivingRouteMode.LIVE, false, routes, quotaReached
                ? "일일 외부 경로 호출 한도에 도달해 먼저 계산된 경로만 반환합니다."
                : "TMAP 자동차 경로 결과입니다. 교통 상황과 내비 재탐색에 따라 달라질 수 있습니다.",
                quotaReached);
    }

    private DrivingRouteCandidate calculateDestination(DrivingRouteRequest request,
            TmapCoordinate origin, DrivingRouteWaypointCandidate candidate) {
        var stops = List.of(candidate);
        TmapRouteSummary summary = tmapRouteClient.findRoute(
                buildSearch(request.tripType(), origin, stops));
        List<Long> legs = summary.legSeconds();
        if (request.tripType() == TripType.ONE_WAY && legs.isEmpty()) {
            // 편도 단일 목적지는 전체 시간이 곧 유일한 구간 시간이다.
            legs = List.of(summary.durationSeconds());
        }
        return new DrivingRouteCandidate(routeId(stops), candidate.name(),
                toWholeMinutes(summary.durationSeconds()), summary.distanceMeters(),
                candidate.theme() == null ? request.theme() : candidate.theme(),
                request.tripType() == TripType.ROUND_TRIP
                        ? summary.repeatedSegmentPercent()
                        : null,
                buildStops(request, stops), "", summary.durationSeconds(), legs);
    }

    /**
     * 단일 후보 두 건과 두 후보 조합을 순서대로 계산해 짧은·긴 경로를 함께 탐색합니다.
     * 앞선 경로가 성공한 뒤 일일 한도가 소진되면 성공 결과를 보존합니다.
     * 인증·네트워크·응답 오류는 부분 성공으로 숨기지 않고 호출자에게 그대로 전달합니다.
     */
    private boolean calculateOptions(
            DrivingRouteRequest request,
            TmapCoordinate origin,
            List<DrivingRouteCandidate> routes) {
        for (List<DrivingRouteWaypointCandidate> scenicStops : buildRouteOptions(request)) {
            try {
                routes.add(calculateOption(request, origin, scenicStops));
            } catch (TmapRouteClientException exception) {
                if (isUnusableRoute(exception)) {
                    continue;
                }
                if (exception.errorType() == TmapClientErrorType.QUOTA_EXCEEDED
                        && !routes.isEmpty()) {
                    return true;
                }
                throw exception;
            }
        }
        return false;
    }

    /** 경로 없음과 비현실적인 거리·시간 조합은 다른 후보 평가를 막지 않습니다. */
    private boolean isUnusableRoute(TmapRouteClientException exception) {
        return exception.errorType() == TmapClientErrorType.NO_ROUTE
                || exception.errorType() == TmapClientErrorType.IMPLAUSIBLE_ROUTE;
    }

    /**
     * 품질 우선순위가 높은 두 후보를 각각 평가한 뒤 두 곳을 잇는 조합을 세 번째로 평가합니다.
     */
    private List<List<DrivingRouteWaypointCandidate>> buildRouteOptions(
            DrivingRouteRequest request) {
        List<DrivingRouteWaypointCandidate> candidates = request.waypointCandidates().stream()
                .limit(MAX_SCENIC_STOPS_PER_ROUTE)
                .toList();
        List<List<DrivingRouteWaypointCandidate>> options = new ArrayList<>();
        for (DrivingRouteWaypointCandidate candidate : candidates) {
            options.add(List.of(candidate));
        }
        if (candidates.size() == MAX_SCENIC_STOPS_PER_ROUTE) {
            options.add(List.copyOf(candidates));
        }
        return options.stream()
                .limit(MAX_EVALUATED_ROUTE_OPTIONS)
                .toList();
    }

    /**
     * 한두 곳의 경관 지점을 주행 방식에 맞는 TMAP 검색 조건으로 변환하고 결과 단위를 정규화합니다.
     */
    private DrivingRouteCandidate calculateOption(
            DrivingRouteRequest request,
            TmapCoordinate origin,
            List<DrivingRouteWaypointCandidate> scenicStops) {
        TmapRouteSearch search = buildSearch(request.tripType(), origin, scenicStops);
        TmapRouteSummary summary = tmapRouteClient.findRoute(search);
        List<DrivingRouteStop> stops = buildStops(request, scenicStops);
        List<Long> legs = summary.legSeconds();
        if (request.tripType() == TripType.ONE_WAY
                && stops.size() == 1
                && legs.isEmpty()) {
            legs = List.of(summary.durationSeconds());
        }

        return new DrivingRouteCandidate(
                routeId(scenicStops),
                routeTitle(scenicStops),
                toWholeMinutes(summary.durationSeconds()),
                summary.distanceMeters(),
                request.theme(),
                request.tripType() == TripType.ROUND_TRIP
                        ? summary.repeatedSegmentPercent()
                        : null,
                stops,
                scenicStops.size() == 1
                        ? "검수된 경관 후보의 TMAP 자동차 경로 시간·거리 결과입니다."
                        : "검수된 두 경관 후보를 잇는 TMAP 자동차 경로 시간·거리 결과입니다.",
                summary.durationSeconds(),
                legs);
    }

    /**
     * 왕복은 모든 경관 후보를 경유해 출발지로 돌아가고, 편도는 마지막 후보에서 종료합니다.
     */
    private List<DrivingRouteStop> buildStops(
            DrivingRouteRequest request,
            List<DrivingRouteWaypointCandidate> scenicStops) {
        List<DrivingRouteStop> stops = new ArrayList<>();
        for (int index = 0; index < scenicStops.size(); index++) {
            DrivingRouteWaypointCandidate candidate = scenicStops.get(index);
            boolean oneWayDestination = request.tripType() == TripType.ONE_WAY
                    && index == scenicStops.size() - 1;
            stops.add(new DrivingRouteStop(
                    index + 1,
                    oneWayDestination
                            ? DrivingRouteStopType.DESTINATION
                            : DrivingRouteStopType.WAYPOINT,
                    candidate.name(),
                    candidate.latitude(),
                    candidate.longitude()));
        }
        if (request.tripType() == TripType.ONE_WAY) {
            return List.copyOf(stops);
        }
        stops.add(new DrivingRouteStop(
                scenicStops.size() + 1,
                DrivingRouteStopType.DESTINATION,
                "출발지 복귀",
                request.latitude(),
                request.longitude()));
        return List.copyOf(stops);
    }

    /** 왕복·편도 규칙에 맞게 마지막 후보는 도착지, 나머지는 경유지로 구성합니다. */
    private TmapRouteSearch buildSearch(
            TripType tripType,
            TmapCoordinate origin,
            List<DrivingRouteWaypointCandidate> scenicStops) {
        if (tripType == TripType.ROUND_TRIP) {
            return new TmapRouteSearch(
                    origin,
                    origin,
                    scenicStops.stream().map(this::toTmapWaypoint).toList());
        }
        DrivingRouteWaypointCandidate destination = scenicStops.getLast();
        TmapCoordinate destinationCoordinate = new TmapCoordinate(
                destination.latitude(), destination.longitude());
        List<TmapWaypoint> waypoints = scenicStops.subList(0, scenicStops.size() - 1)
                .stream()
                .map(this::toTmapWaypoint)
                .toList();
        return new TmapRouteSearch(origin, destinationCoordinate, waypoints);
    }

    /** 내부 경관 후보를 비밀값 없이 TMAP 경유지 형식으로 변환합니다. */
    private TmapWaypoint toTmapWaypoint(DrivingRouteWaypointCandidate candidate) {
        return new TmapWaypoint(
                Long.toString(candidate.spotId()),
                candidate.name(),
                new TmapCoordinate(candidate.latitude(), candidate.longitude()));
    }

    /** 단일 경로의 기존 식별자를 유지하고 조합 경로는 두 장소 ID를 순서대로 포함합니다. */
    private String routeId(List<DrivingRouteWaypointCandidate> scenicStops) {
        if (scenicStops.size() == 1) {
            return "TMAP-SPOT-" + scenicStops.getFirst().spotId();
        }
        return "TMAP-SPOTS-" + scenicStops.stream()
                .map(candidate -> Long.toString(candidate.spotId()))
                .collect(Collectors.joining("-"));
    }

    /** 사용자가 경유 순서를 이해할 수 있도록 경관 후보 이름을 순서대로 표시합니다. */
    private String routeTitle(List<DrivingRouteWaypointCandidate> scenicStops) {
        return scenicStops.stream()
                .map(DrivingRouteWaypointCandidate::name)
                .collect(Collectors.joining(" · "))
                + " 드라이브";
    }

    /** 초 단위 시간을 부족하게 표시하지 않도록 올림한 분 단위로 변환합니다. */
    private int toWholeMinutes(long durationSeconds) {
        return Math.toIntExact((durationSeconds + 59L) / 60L);
    }
}
