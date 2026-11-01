package com.example.justgo.backend.integration.routing.tmap;

/**
 * TMAP 응답에서 추천 점수 계산에 필요한 총 거리와 총 소요 시간만 분리한 결과입니다.
 */
public record TmapRouteSummary(long distanceMeters, long durationSeconds) {
}
