package com.example.justgo.backend.integration.routing;

import java.math.BigDecimal;
import com.example.justgo.backend.spot.ScenicTheme;

/**
 * 자동차 경로 제공자가 경유지로 검토할 수 있는 검수 완료 장소 후보입니다.
 * 좌표는 WGS84 위도·경도이며 실제 경유지 채택 여부는 제공자 구현이 결정합니다.
 */
public record DrivingRouteWaypointCandidate(
        long spotId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        ScenicTheme theme) {

    public DrivingRouteWaypointCandidate(long spotId, String name, BigDecimal latitude, BigDecimal longitude) {
        this(spotId, name, latitude, longitude, null);
    }
}
