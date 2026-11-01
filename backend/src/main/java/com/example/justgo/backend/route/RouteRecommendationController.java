package com.example.justgo.backend.route;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 추천 HTTP 요청을 받고 서비스 계층에 전달하는 REST 진입점입니다.
 * 비즈니스 규칙과 외부 API 호출은 이 클래스에 두지 않습니다.
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteRecommendationController {

    private final RouteRecommendationService routeRecommendationService;

    public RouteRecommendationController(RouteRecommendationService routeRecommendationService) {
        this.routeRecommendationService = routeRecommendationService;
    }

    /**
     * 입력값을 Bean Validation으로 검사하고 추천 결과를 반환합니다.
     */
    @PostMapping("/recommendations")
    public ResponseEntity<RouteRecommendationResponse> recommend(
            @Valid @RequestBody RouteRecommendationRequest request) {
        return ResponseEntity.ok(routeRecommendationService.recommend(request));
    }
}
