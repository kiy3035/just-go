package com.example.justgo.backend.integration.routing.tmap;

import java.util.List;

/**
 * TMAP 응답에서 추천에 필요한 총 거리·시간, 구간 시간과 임시 경로 중복 지표를 분리한 결과입니다.
 * 형상을 제공하지 않거나 분석할 수 없으면 repeatedSegmentPercent는 null입니다.
 */
public record TmapRouteSummary(
        long distanceMeters,
        long durationSeconds,
        Integer repeatedSegmentPercent,
        List<Long> legSeconds) {

    /** 구간 시간이 없는 기존 요약 응답은 총 거리·시간만 보존합니다. */
    public TmapRouteSummary(
            long distanceMeters,
            long durationSeconds,
            Integer repeatedSegmentPercent) {
        this(distanceMeters, durationSeconds, repeatedSegmentPercent, List.of());
    }

    /** 외부 응답에서 만든 구간 목록이 이후 변경되지 않도록 복사합니다. */
    public TmapRouteSummary {
        legSeconds = legSeconds == null ? List.of() : List.copyOf(legSeconds);
    }
}
