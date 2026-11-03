package com.example.justgo.backend.route;

import java.util.Comparator;
import java.util.List;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.integration.routing.DrivingRouteMode;
import com.example.justgo.backend.integration.routing.DrivingRouteProvider;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import com.example.justgo.backend.integration.routing.DrivingRouteWaypointCandidate;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import com.example.justgo.backend.spot.SpotCandidateRecord;
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
    private static final int MAX_CONDITION_SUGGESTIONS = 3;

    private final DrivingRouteProvider drivingRouteProvider;
    private final SpotCandidateQuery spotCandidateQuery;
    private final RouteRecommendationPolicy recommendationPolicy = new RouteRecommendationPolicy();

    public RouteRecommendationService(
            DrivingRouteProvider drivingRouteProvider,
            SpotCandidateQuery spotCandidateQuery) {
        this.drivingRouteProvider = drivingRouteProvider;
        this.spotCandidateQuery = spotCandidateQuery;
    }

    /**
     * 입력 시간 단위를 검증하고 LIVE 순위 또는 실제 후보가 있는 대체 조건을 적용합니다.
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

        var waypointCandidates = spotCandidateQuery
                .loadVerifiedCandidates(request.theme(), MAX_WAYPOINT_CANDIDATES)
                .stream()
                .map(this::toWaypointCandidate)
                .toList();
        DrivingRouteRequest providerRequest = new DrivingRouteRequest(
                request.latitude(),
                request.longitude(),
                request.desiredMinutes(),
                request.tripType(),
                request.theme(),
                waypointCandidates);
        DrivingRouteResult result = drivingRouteProvider.calculate(providerRequest);
        var recommendedRoutes = result.mode() == DrivingRouteMode.LIVE
                ? recommendationPolicy.rankWithinDurationTolerance(
                        request.desiredMinutes(),
                        result.routes())
                : result.routes();
        boolean needsAlternativeDuration = result.mode() == DrivingRouteMode.LIVE
                && !result.routes().isEmpty()
                && recommendedRoutes.isEmpty();
        List<RouteConditionSuggestion> suggestions = needsAlternativeDuration
                ? recommendationPolicy.suggestAlternativeDurations(
                                request.desiredMinutes(),
                                result.routes())
                        .stream()
                        .map(minutes -> new RouteConditionSuggestion(
                                minutes,
                                request.tripType(),
                                request.theme(),
                                "실제 계산 후보의 예상 시간과 가까운 조건입니다."))
                        .toList()
                : suggestAlternativeThemes(
                        request,
                        result.mode() != DrivingRouteMode.MOCK && waypointCandidates.isEmpty());
        String notice = needsAlternativeDuration
                ? alternativeDurationNotice(suggestions)
                : alternativeThemeNotice(result.notice(), suggestions);

        return new RouteRecommendationResponse(
                result.mode().name(),
                result.mock(),
                recommendedRoutes.stream().map(this::toResponseItem).toList(),
                suggestions,
                notice);
    }

    /** 대체 시간이 있으면 재검색을 안내하고, 없으면 일반적인 조건 변경을 안내합니다. */
    private String alternativeDurationNotice(List<RouteConditionSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return "희망 주행 시간 ±%d%%에 맞는 검수 코스를 찾지 못했습니다. 시간이나 풍경 조건을 바꿔 주세요."
                    .formatted(RouteRecommendationPolicy.DURATION_TOLERANCE_PERCENT);
        }
        return "희망 주행 시간 ±%d%%에 맞는 검수 코스를 찾지 못했습니다. 제안된 시간으로 다시 검색해 주세요."
                .formatted(RouteRecommendationPolicy.DURATION_TOLERANCE_PERCENT);
    }

    /** 선택 테마에 후보가 없을 때만 실제 검수 후보가 있는 다른 테마를 제안합니다. */
    private List<RouteConditionSuggestion> suggestAlternativeThemes(
            RouteRecommendationRequest request,
            boolean selectedThemeHasNoCandidates) {
        if (!selectedThemeHasNoCandidates) {
            return List.of();
        }
        return spotCandidateQuery.loadAvailableThemes().stream()
                .filter(theme -> theme != request.theme())
                .distinct()
                .sorted(Comparator.comparingInt(ScenicTheme::ordinal))
                .limit(MAX_CONDITION_SUGGESTIONS)
                .map(theme -> new RouteConditionSuggestion(
                        request.desiredMinutes(),
                        request.tripType(),
                        theme,
                        "이 풍경에는 검수 완료 후보가 있습니다. 실제 경로 가능 여부는 재검색이 필요합니다."))
                .toList();
    }

    /** 다른 풍경 제안이 있으면 현재 결과 상태를 유지하면서 재검색 목적을 분명히 안내합니다. */
    private String alternativeThemeNotice(
            String providerNotice,
            List<RouteConditionSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return providerNotice;
        }
        return "선택한 풍경에는 검수 완료 후보가 없습니다. 제안된 다른 풍경으로 다시 검색해 주세요.";
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
