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
        if (estimatedMinutes < 0 || durationSeconds < 0
                || estimatedMinutes != wholeMinutes(durationSeconds)) {
            throw new IllegalArgumentException("경로 예상 분과 전체 초가 일치하지 않습니다.");
        }
        if (repeatedSegmentPercent != null
                && (repeatedSegmentPercent < 0 || repeatedSegmentPercent > 100)) {
            throw new IllegalArgumentException("경로 중복률은 0~100%여야 합니다.");
        }
        stops = List.copyOf(stops);
        legSeconds = List.copyOf(legSeconds);
        if (!legSeconds.isEmpty()) {
            if (legSeconds.size() != stops.size()) {
                throw new IllegalArgumentException("경로 구간 시간은 지점 수와 일치해야 합니다.");
            }
            long measuredSeconds = 0;
            for (long leg : legSeconds) {
                if (leg <= 0 || leg > durationSeconds - measuredSeconds) {
                    throw new IllegalArgumentException("경로 구간 시간이 올바르지 않습니다.");
                }
                measuredSeconds += leg;
            }
            if (measuredSeconds != durationSeconds) {
                throw new IllegalArgumentException("경로 구간 시간 합계가 전체 시간과 다릅니다.");
            }
        }
    }

    private static long wholeMinutes(long durationSeconds) {
        return durationSeconds / 60L + (durationSeconds % 60L == 0 ? 0 : 1);
    }
}
