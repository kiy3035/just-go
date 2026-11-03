package com.example.justgo.backend.integration.routing.tmap;

/**
 * TMAP 응답에서 추천에 필요한 총 거리·시간과 임시 경로 중복 지표만 분리한 결과입니다.
 * 형상을 제공하지 않거나 분석할 수 없으면 repeatedSegmentPercent는 null입니다.
 */
public record TmapRouteSummary(
        long distanceMeters,
        long durationSeconds,
        Integer repeatedSegmentPercent) {
}
