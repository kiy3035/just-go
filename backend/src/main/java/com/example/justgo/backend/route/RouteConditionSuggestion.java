package com.example.justgo.backend.route;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 실제 계산된 후보 시간 또는 검수 후보가 존재하는 풍경을 근거로 재검색 조건을 안내합니다.
 * 경로 자체가 아니므로 예상 거리나 좌표를 포함하지 않습니다.
 */
public record RouteConditionSuggestion(
        int desiredMinutes,
        TripType tripType,
        ScenicTheme theme,
        String reason) {
}
