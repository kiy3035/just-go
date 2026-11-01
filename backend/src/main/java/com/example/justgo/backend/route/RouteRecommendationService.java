package com.example.justgo.backend.route;

import java.util.Set;

import com.example.justgo.backend.integration.routing.DrivingRouteCandidate;
import com.example.justgo.backend.integration.routing.DrivingRouteProvider;
import com.example.justgo.backend.integration.routing.DrivingRouteRequest;
import com.example.justgo.backend.integration.routing.DrivingRouteResult;
import org.springframework.stereotype.Service;

/**
 * 추천 요청을 검증하고 교체 가능한 자동차 경로 제공자에 위임합니다.
 * 외부 HTTP 호출을 데이터베이스 트랜잭션에 묶지 않습니다.
 */
@Service
public class RouteRecommendationService {

    private static final Set<Integer> SUPPORTED_DURATIONS = Set.of(30, 60, 90, 120);

    private final DrivingRouteProvider drivingRouteProvider;

    public RouteRecommendationService(DrivingRouteProvider drivingRouteProvider) {
        this.drivingRouteProvider = drivingRouteProvider;
    }

    /**
     * 제품에서 허용한 시간 단위를 확인한 후 제공자 결과를 API 응답으로 변환합니다.
     *
     * @param request 검증된 외부 요청
     * @return MOCK 또는 미연동 상태가 명시된 추천 응답
     */
    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        if (!SUPPORTED_DURATIONS.contains(request.desiredMinutes())) {
            throw new UnsupportedDrivingDurationException(request.desiredMinutes());
        }

        DrivingRouteRequest providerRequest = new DrivingRouteRequest(
                request.latitude(),
                request.longitude(),
                request.desiredMinutes(),
                request.theme());
        DrivingRouteResult result = drivingRouteProvider.calculate(providerRequest);

        return new RouteRecommendationResponse(
                result.mode().name(),
                result.mock(),
                result.routes().stream().map(this::toResponseItem).toList(),
                result.notice());
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
                candidate.reason());
    }
}
