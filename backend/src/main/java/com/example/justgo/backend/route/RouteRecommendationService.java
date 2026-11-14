package com.example.justgo.backend.route;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.justgo.backend.integration.place.CityEventSource;
import com.example.justgo.backend.integration.place.PublicDataClientException;
import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.integration.routing.DrivingRouteLimitException;
import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteProvider;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import com.example.justgo.backend.integration.routing.DrivingRouteWaypointCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.ScenicPlaceDistance;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 추천 요청을 검증하고 교체 가능한 자동차 경로 제공자에 위임합니다.
 * 외부 HTTP 호출을 데이터베이스 트랜잭션에 묶지 않습니다.
 */
@Service
public class RouteRecommendationService {

    private static final int MIN_DURATION_MINUTES = 10;
    private static final int MAX_DURATION_MINUTES = 240;
    private static final int DURATION_STEP_MINUTES = 5;
    private static final int MAX_WAYPOINT_CANDIDATES = 20;
    private static final int ROUTES_PER_STAGE = 5;
    private static final int TARGET_RECOMMENDATION_COUNT = 5;

    private final DrivingRouteProvider drivingRouteProvider;
    private final SpotCandidateQuery spotCandidateQuery;
    private final CandidatePlaceSelectionStore selectionStore;
    private final RouteRecommendationPolicy recommendationPolicy;
    private final CityEventSource cityEventSource;
    private final RouteCandidateDistancePolicy candidateDistancePolicy =
            new RouteCandidateDistancePolicy();

    /** 외부 경로 제공자와 검수 후보 조회기를 주입합니다. */
    @Autowired
    public RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery,
            CandidatePlaceSelectionStore selectionStore,
            ObjectProvider<CityEventSource> eventSource) {
        this(drivingRouteProvider, spotCandidateQuery, selectionStore, eventSource.getIfAvailable());
    }

    private RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery,
            CandidatePlaceSelectionStore selectionStore,
            CityEventSource cityEventSource) {
        this.drivingRouteProvider = drivingRouteProvider;
        this.spotCandidateQuery = spotCandidateQuery;
        this.selectionStore = selectionStore;
        this.recommendationPolicy = new RouteRecommendationPolicy();
        this.cityEventSource = cityEventSource;
    }

    RouteRecommendationService(DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery, CandidatePlaceSelectionStore selectionStore) {
        this(drivingRouteProvider, spotCandidateQuery, selectionStore, (CityEventSource) null);
    }

    /** 단위 테스트와 기존 직접 생성 코드는 빈 단기 저장소를 사용합니다. */
    RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery) {
        this(drivingRouteProvider, spotCandidateQuery, new CandidatePlaceSelectionStore());
    }

    /**
     * 입력 시간 단위를 검증하고 모든 LIVE 계산 결과를 희망 시간과 가까운 순으로 반환합니다.
     *
     * @param request 검증된 외부 요청
     * @return 데이터 출처 모드와 추천 결과가 명시된 응답
     */
    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        if (request.desiredMinutes() < MIN_DURATION_MINUTES
                || request.desiredMinutes() > MAX_DURATION_MINUTES
                || request.desiredMinutes() % DURATION_STEP_MINUTES != 0) {
            throw new UnsupportedDrivingDurationException(request.desiredMinutes());
        }

        long maxCandidateDistanceMeters = candidateDistancePolicy
                .maxStraightLineDistanceMeters(request.desiredMinutes(), request.tripType());
        var search = request.candidateToken() == null
                ? searchRoutes(request, maxCandidateDistanceMeters)
                : new SearchResult(calculate(request, request.theme(),
                        selectedCandidate(request, maxCandidateDistanceMeters)), Map.of(), false);
        DrivingRouteResult result = search.result();
        var recommendedRoutes = result.mode() == DrivingRouteMode.LIVE
                ? recommendationPolicy.rankByDurationProximity(
                        request.desiredMinutes(),
                        result.routes(), search.events().keySet())
                : result.routes();
        var alternatives = result.mode() == DrivingRouteMode.LIVE
                && recommendedRoutes.isEmpty() && !result.quotaReached()
                && request.candidateToken() == null && request.theme() != ScenicTheme.ANYWHERE
                ? calculateAlternatives(request, maxCandidateDistanceMeters * 3 / 2)
                : List.<DrivingRouteCandidate>of();
        if (recommendedRoutes.isEmpty() && alternatives.isEmpty() && search.sourceUnavailable()
                && result.mode() == DrivingRouteMode.LIVE) {
            throw new PublicDataClientException("장소 후보 서비스가 응답하지 못했습니다.");
        }
        String notice = alternatives.isEmpty()
                ? durationMatchedLiveResultNotice(result, recommendedRoutes)
                : "선택한 풍경의 코스가 없어 가까운 다른 풍경의 실제 경로를 찾았습니다.";

        return new RouteRecommendationResponse(
                result.mode().name(),
                result.mock(),
                recommendedRoutes.stream().map(route -> toResponseItem(route, search.events().get(route.routeId()))).toList(),
                alternatives.stream().map(route -> toResponseItem(route, null)).toList(),
                List.of(),
                notice);
    }

    /** 각 단계에서 새 목적지를 조회하고, 실제 경로를 확보할 때까지 검색 반경을 넓힌다. */
    private SearchResult searchRoutes(RouteRecommendationRequest request, long baseRadius) {
        ZonedDateTime departure = ZonedDateTime.now(CityEventSource.SEOUL);
        List<CityEventSource.Event> events = request.theme() == ScenicTheme.CITY && cityEventSource != null
                ? cityEventSource.events(departure) : List.of();
        List<DrivingRouteCandidate> routes = new ArrayList<>();
        Map<String, RouteRecommendationItem.EventDetails> eventDetails = new HashMap<>();
        Set<Long> attempted = new HashSet<>();
        boolean sourceUnavailable = false;
        String notice = "자동차 경로를 찾지 못했습니다.";
        boolean quotaReached = false;
        boolean providerChecked = false;
        for (long radius : new long[]{baseRadius, baseRadius * 5 / 4, baseRadius * 3 / 2}) {
            var nearby = spotCandidateQuery.searchRecommendationCandidates(request.theme(), request.latitude(),
                    request.longitude(), radius, 50);
            sourceUnavailable |= nearby.partial();
            List<SpotCandidateRecord> batch = new ArrayList<>();
            Map<String, CityEventSource.Event> batchEvents = new HashMap<>();
            // 일부 호출은 일반 명소에 남겨 두어 행사 시간이 맞지 않아도 도시 결과를 확보한다.
            events.stream().filter(event -> !attempted.contains(event.venue().id()))
                    .filter(event -> distance(request, event.venue()) <= radius)
                    .limit(3).forEach(event -> {
                        if (attempted.add(event.venue().id())) {
                            batch.add(event.venue());
                            batchEvents.put("TMAP-SPOT-" + event.venue().id(), event);
                        }
                    });
            // 후보 조회기가 여러 거리 구간을 섞었으므로 그 순서에서 아직 확인하지 않은 장소를 사용한다.
            for (SpotCandidateRecord candidate : nearby.candidates()) {
                if (batch.size() >= ROUTES_PER_STAGE) break;
                if (attempted.add(candidate.id())) batch.add(candidate);
            }
            if (batch.isEmpty() && providerChecked) continue;
            providerChecked = true;

            DrivingRouteResult result = drivingRouteProvider.calculateRanked(new DrivingRouteRequest(
                    request.latitude(), request.longitude(), request.desiredMinutes(), request.tripType(),
                    request.theme(), batch.stream().map(this::toWaypointCandidate).toList()));
            if (result.mode() != DrivingRouteMode.LIVE) {
                return new SearchResult(result, Map.of(), sourceUnavailable);
            }
            for (DrivingRouteCandidate route : result.routes()) {
                var event = batchEvents.get(route.routeId());
                if (event != null) {
                    // 왕복의 전체 시간과 별개로, 첫 구간만 사용해 행사장 도착 시각을 판단한다.
                    if (route.legSeconds().isEmpty()) continue;
                    ZonedDateTime arrival = departure.plusSeconds(route.legSeconds().getFirst());
                    if (!event.canVisitAt(arrival)) continue;
                    eventDetails.put(route.routeId(), new RouteRecommendationItem.EventDetails(event.name(),
                            event.venue().name(), event.opens().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            event.closes().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            arrival.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
                }
                if (recommendationPolicy.isWithinDurationTolerance(
                        request.desiredMinutes(), route)) {
                    routes.add(route);
                }
            }
            notice = result.notice();
            quotaReached = result.quotaReached();
            if (quotaReached || routes.size() >= TARGET_RECOMMENDATION_COUNT) break;
        }
        if (quotaReached && routes.isEmpty()) {
            throw new DrivingRouteLimitException();
        }
        return new SearchResult(new DrivingRouteResult(DrivingRouteMode.LIVE, false, routes, notice, quotaReached),
                eventDetails, sourceUnavailable);
    }

    private long distance(RouteRecommendationRequest request, SpotCandidateRecord candidate) {
        return ScenicPlaceDistance.meters(request.latitude(), request.longitude(),
                candidate.latitude(), candidate.longitude());
    }

    private record SearchResult(DrivingRouteResult result, Map<String, RouteRecommendationItem.EventDetails> events,
                                boolean sourceUnavailable) {}

    private DrivingRouteResult calculate(
            RouteRecommendationRequest request, ScenicTheme theme, List<SpotCandidateRecord> candidates) {
        return drivingRouteProvider.calculate(new DrivingRouteRequest(
                request.latitude(), request.longitude(), request.desiredMinutes(), request.tripType(),
                theme, candidates.stream().map(this::toWaypointCandidate).toList()));
    }

    /** 원래 풍경의 경로가 없으면 다른 풍경을 실제로 계산해 별도 결과로 제공한다. */
    private List<DrivingRouteCandidate> calculateAlternatives(
            RouteRecommendationRequest request, long maxCandidateDistanceMeters) {
        var nearby = spotCandidateQuery.loadNearbyVerifiedCandidates(
                        ScenicTheme.ANYWHERE, request.latitude(), request.longitude(),
                        maxCandidateDistanceMeters, MAX_WAYPOINT_CANDIDATES)
                .stream()
                .filter(candidate -> !request.theme().includes(candidate.theme()))
                .toList();
        if (nearby.isEmpty()) return List.of();

        var destinations = nearby.stream()
                .limit(ROUTES_PER_STAGE)
                .map(this::toWaypointCandidate)
                .toList();
        ScenicTheme firstTheme = destinations.getFirst().theme();
        ScenicTheme alternativeTheme = destinations.stream().allMatch(candidate -> candidate.theme() == firstTheme)
                ? firstTheme : ScenicTheme.ANYWHERE;
        var result = drivingRouteProvider.calculateRanked(new DrivingRouteRequest(request.latitude(),
                request.longitude(), request.desiredMinutes(), request.tripType(), alternativeTheme, destinations));
        if (result.quotaReached() && result.routes().isEmpty()) throw new DrivingRouteLimitException();
        return result.mode() == DrivingRouteMode.LIVE
                ? recommendationPolicy.rankByDurationProximity(request.desiredMinutes(), result.routes())
                : List.of();
    }

    /** 기존 선택 토큰 호출은 선택한 한 장소의 계약을 유지한다. */
    private List<SpotCandidateRecord> selectedCandidate(
            RouteRecommendationRequest request,
            long maxCandidateDistanceMeters) {
        SpotCandidateRecord selected = selectionStore.resolve(request.candidateToken())
                .orElseThrow(InvalidCandidateTokenException::new);
        long distanceMeters = ScenicPlaceDistance.meters(
                request.latitude(),
                request.longitude(),
                selected.latitude(),
                selected.longitude());
        if (!request.theme().includes(selected.theme())
                || distanceMeters > maxCandidateDistanceMeters) {
            throw new InvalidCandidateTokenException();
        }
        return List.of(selected);
    }

    /** LIVE 결과가 목표 시간 허용범위를 충족했는지 사용자에게 분명히 알립니다. */
    private String durationMatchedLiveResultNotice(
            DrivingRouteResult result,
            List<DrivingRouteCandidate> recommendedRoutes) {
        if (result.mode() != DrivingRouteMode.LIVE) {
            return result.notice();
        }
        if (recommendedRoutes.isEmpty()) {
            return "목표 시간 ±15% 안의 자동차 경로를 찾지 못했습니다. 시간이나 풍경 조건을 변경해 주세요.";
        }
        return result.notice()
                + " 목표 시간 ±15% 안의 경로만 가까운 순으로 표시합니다.";
    }

    /**
     * 내부 DB 모델에서 외부 경로 제공자에 필요한 최소 식별자와 좌표만 전달합니다.
     */
    private DrivingRouteWaypointCandidate toWaypointCandidate(SpotCandidateRecord candidate) {
        return new DrivingRouteWaypointCandidate(
                candidate.id(),
                candidate.name(),
                candidate.latitude(),
                candidate.longitude(), candidate.theme());
    }

    /**
     * 외부 제공자 모델을 API DTO와 분리해 변환합니다.
     */
    private RouteRecommendationItem toResponseItem(DrivingRouteCandidate candidate,
            RouteRecommendationItem.EventDetails event) {
        return new RouteRecommendationItem(
                candidate.routeId(),
                candidate.title(),
                candidate.estimatedMinutes(),
                candidate.distanceMeters(),
                candidate.theme(),
                candidate.repeatedSegmentPercent(),
                candidate.stops().stream()
                        .map(stop -> new RouteRecommendationStop(
                                stop.sequence(),
                                stop.type().name(),
                                stop.name(),
                                stop.latitude(),
                                stop.longitude()))
                        .toList(),
                candidate.reason(), candidate.durationSeconds(), candidate.legSeconds(), event);
    }
}
