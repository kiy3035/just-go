package com.example.justgo.backend.route;

import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 실제 계산된 후보 시간을 근거로 사용자가 다시 검색할 수 있는 조건을 안내합니다.
 * 경로 자체가 아니므로 예상 거리나 좌표를 포함하지 않습니다.
 */
public record RouteConditionSuggestion(
        int desiredMinutes,
        TripType tripType,
        ScenicTheme theme,
        String reason) {
}
