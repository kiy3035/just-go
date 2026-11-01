package com.example.justgo.backend.integration.routing;

/**
 * 경로 계산 결과가 실제 제공자 결과인지 개발용 대체 결과인지 구분합니다.
 */
public enum DrivingRouteMode {
    MOCK,
    LIVE,
    UNAVAILABLE
}
