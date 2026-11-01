package com.example.justgo.backend.route;

/**
 * 사용자가 선택한 주행 종료 방식입니다.
 * 왕복은 출발지 복귀, 편도는 출발지와 다른 종료 지점을 의미합니다.
 */
public enum TripType {
    ROUND_TRIP,
    ONE_WAY
}
