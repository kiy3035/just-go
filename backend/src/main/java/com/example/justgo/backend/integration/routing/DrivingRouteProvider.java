package com.example.justgo.backend.integration.routing;

/**
 * 첫 PoC 제공자인 TMAP 구현을 내부에 격리하고 향후에도 교체 가능하게 유지하는 경계입니다.
 */
public interface DrivingRouteProvider {

    /**
     * 주어진 조건으로 자동차 경로 후보를 계산합니다.
     *
     * @param request WGS84 출발 좌표, 희망 시간·테마, 검수 완료 경유지 후보
     * @return 제공자 모드와 코스 후보
     */
    DrivingRouteResult calculate(DrivingRouteRequest request);

    /** 추천 목록을 위해 한 묶음의 목적지를 개별 코스로 계산한다. */
    default DrivingRouteResult calculateRanked(DrivingRouteRequest request) {
        return calculate(request);
    }
}
