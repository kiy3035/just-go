package com.example.justgo.backend.integration.routing;

import java.util.List;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 경로 제공자가 계산한 코스 후보 한 건입니다.
 * MOCK 구현은 가짜 좌표를 만들지 않기 위해 지점 목록을 비워 둡니다.
 */
public record DrivingRouteCandidate(
        String routeId,
        String title,
        int estimatedMinutes,
        long distanceMeters,
        ScenicTheme theme,
        Integer repeatedSegmentPercent,
        List<DrivingRouteStop> stops,
        String reason,
        long durationSeconds,
        List<Long> legSeconds) {

    public DrivingRouteCandidate(String routeId, String title, int estimatedMinutes,
            long distanceMeters, ScenicTheme theme, Integer repeatedSegmentPercent,
            List<DrivingRouteStop> stops, String reason) {
        this(routeId, title, estimatedMinutes, distanceMeters, theme, repeatedSegmentPercent,
                stops, reason, estimatedMinutes * 60L, List.of());
    }

    /** 응답 변환 중 지점 목록이 바뀌지 않도록 방어적으로 복사합니다. */
    public DrivingRouteCandidate {
        if (repeatedSegmentPercent != null
                && (repeatedSegmentPercent < 0 || repeatedSegmentPercent > 100)) {
            throw new IllegalArgumentException("경로 중복률은 0~100%여야 합니다.");
        }
        stops = List.copyOf(stops);
        legSeconds = List.copyOf(legSeconds);
    }
}
