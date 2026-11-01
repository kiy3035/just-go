package com.example.justgo.backend.integration.routing;

/**
 * TMAP·카카오모빌리티·NAVER 중 어느 제공자도 확정하지 않고 교체 가능하게 유지하는 경계입니다.
 */
public interface DrivingRouteProvider {

    /**
     * 주어진 조건으로 자동차 경로 후보를 계산합니다.
     *
     * @param request WGS84 출발 좌표, 희망 시간·테마, 검수 완료 경유지 후보
     * @return 제공자 모드와 코스 후보
     */
    DrivingRouteResult calculate(DrivingRouteRequest request);
}
