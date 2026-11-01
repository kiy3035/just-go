package com.example.justgo.backend.integration.routing;

import java.util.List;

/**
 * 경로 제공자의 계산 결과와 데이터 출처 상태를 함께 전달합니다.
 */
public record DrivingRouteResult(
        DrivingRouteMode mode,
        boolean mock,
        List<DrivingRouteCandidate> routes,
        String notice) {

    /**
     * 외부에서 전달된 목록이 이후 변경되지 않도록 방어적으로 복사합니다.
     */
    public DrivingRouteResult {
        routes = List.copyOf(routes);
    }
}
