package com.example.justgo.backend.route;

import static com.example.justgo.backend.integration.routing.DrivingRouteProvider.MAX_ROUTE_OPTIONS_PER_REQUEST;

import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
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

    private final DrivingRouteProvider drivingRouteProvider;
    private final SpotCandidateQuery spotCandidateQuery;
    private final CandidatePlaceSelectionStore selectionStore;
    private final RouteRecommendationPolicy recommendationPolicy;
    private final RouteCandidateDistancePolicy candidateDistancePolicy =
            new RouteCandidateDistancePolicy();

    /** 외부 경로 제공자와 검수 후보 조회기를 주입합니다. */
    @Autowired
    public RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery,
            CandidatePlaceSelectionStore selectionStore) {
        this.drivingRouteProvider = drivingRouteProvider;
        this.spotCandidateQuery = spotCandidateQuery;
        this.selectionStore = selectionStore;
        this.recommendationPolicy = new RouteRecommendationPolicy();
    }

    /** 단위 테스트와 기존 직접 생성 코드는 빈 단기 저장소를 사용합니다. */
    RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery) {
        this(drivingRouteProvider, spotCandidateQuery, new CandidatePlaceSelectionStore());
    }

    /**
     * 입력 시간 단위를 검증하고 희망 시간 범위 안의 LIVE 결과를 가까운 순으로 반환합니다.
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
        var candidates = selectedOrDiscoveredCandidates(request, maxCandidateDistanceMeters);
        DrivingRouteResult result = calculate(request, request.theme(), candidates);
        var recommendedRoutes = result.mode() == DrivingRouteMode.LIVE
                ? recommendationPolicy.rankByDurationProximity(
                        request.desiredMinutes(),
                        result.routes())
                : result.routes();
        var alternatives = result.mode() == DrivingRouteMode.LIVE
                && recommendedRoutes.isEmpty() && candidates.size() <= 1
                && !request.theme().isDining()
                && request.candidateToken() == null && request.theme() != ScenicTheme.ANYWHERE
                ? calculateAlternatives(request, maxCandidateDistanceMeters,
                        MAX_ROUTE_OPTIONS_PER_REQUEST - candidates.size() - 1)
                : List.<DrivingRouteCandidate>of();
        String notice = alternatives.isEmpty()
                ? result.notice()
                : "선택한 풍경의 코스가 없어 가까운 다른 풍경의 실제 경로를 찾았습니다.";

        return new RouteRecommendationResponse(
                result.mode().name(),
                result.mock(),
                recommendedRoutes.stream().map(this::toResponseItem).toList(),
                alternatives.stream().map(this::toResponseItem).toList(),
                List.of(),
                notice);
    }

    private DrivingRouteResult calculate(
            RouteRecommendationRequest request, ScenicTheme theme, List<SpotCandidateRecord> candidates) {
        return drivingRouteProvider.calculate(new DrivingRouteRequest(
                request.latitude(), request.longitude(), request.desiredMinutes(), request.tripType(),
                theme, candidates.stream().map(this::toWaypointCandidate).toList()));
    }

    /** 원래 후보가 없거나 한 곳에서 경로를 못 찾았으면 남은 호출 예산을 대체 풍경에 사용합니다. */
    private List<DrivingRouteCandidate> calculateAlternatives(
            RouteRecommendationRequest request, long maxCandidateDistanceMeters, int candidateLimit) {
        var nearby = spotCandidateQuery.loadNearbyVerifiedCandidates(
                        ScenicTheme.ANYWHERE, request.latitude(), request.longitude(),
                        maxCandidateDistanceMeters, MAX_WAYPOINT_CANDIDATES)
                .stream()
                .filter(candidate -> !request.theme().includes(candidate.theme()))
                .toList();
        if (nearby.isEmpty()) return List.of();

        ScenicTheme alternativeTheme = nearby.getFirst().theme();
        var result = calculate(request, alternativeTheme, nearby.stream()
                .filter(candidate -> candidate.theme() == alternativeTheme)
                .limit(candidateLimit)
                .toList());
        return result.mode() == DrivingRouteMode.LIVE
                ? recommendationPolicy.rankByDurationProximity(request.desiredMinutes(), result.routes())
                : List.of();
    }

    /** 선택 토큰이 있으면 그 장소 한 곳만, 없으면 기존 자동 후보 목록을 사용합니다. */
    private List<SpotCandidateRecord> selectedOrDiscoveredCandidates(
            RouteRecommendationRequest request,
            long maxCandidateDistanceMeters) {
        if (request.candidateToken() == null) {
            return spotCandidateQuery.loadNearbyVerifiedCandidates(
                    request.theme(),
                    request.latitude(),
                    request.longitude(),
                    maxCandidateDistanceMeters,
                    MAX_WAYPOINT_CANDIDATES);
        }
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

    /**
     * 내부 DB 모델에서 외부 경로 제공자에 필요한 최소 식별자와 좌표만 전달합니다.
     */
    private DrivingRouteWaypointCandidate toWaypointCandidate(SpotCandidateRecord candidate) {
        return new DrivingRouteWaypointCandidate(
                candidate.id(),
                candidate.name(),
                candidate.latitude(),
                candidate.longitude());
    }

    /**
     * 외부 제공자 모델을 API DTO와 분리해 변환합니다.
     */
    private RouteRecommendationItem toResponseItem(DrivingRouteCandidate candidate) {
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
                candidate.reason());
    }
}
