package com.example.justgo.backend.route;

import java.util.List;

/**
 * 추천 결과와 데이터 출처 모드를 함께 반환해 MOCK 결과의 오인을 막습니다.
 */
public record RouteRecommendationResponse(
        String mode,
        boolean mock,
        List<RouteRecommendationItem> routes,
        List<RouteConditionSuggestion> suggestions,
        String notice) {

    /**
     * 응답 생성 이후 코스 목록이 바뀌지 않도록 방어적으로 복사합니다.
     */
    public RouteRecommendationResponse {
        routes = List.copyOf(routes);
        suggestions = List.copyOf(suggestions);
    }
}
