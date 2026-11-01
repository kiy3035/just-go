package com.example.justgo.backend.integration.routing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 추천 코스에서 차량 내비게이션에 순서대로 전달할 지점입니다.
 * 좌표는 WGS84 위도·경도를 사용하며 사용자 위치를 저장하거나 로그에 남기지 않습니다.
 */
public record DrivingRouteStop(
        int sequence,
        DrivingRouteStopType type,
        String name,
        BigDecimal latitude,
        BigDecimal longitude) {

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    /** 잘못된 순서나 좌표가 Android 내비 전달 단계까지 넘어가지 않도록 검증합니다. */
    public DrivingRouteStop {
        if (sequence < 1) {
            throw new IllegalArgumentException("경로 지점 순서는 1 이상이어야 합니다.");
        }
        Objects.requireNonNull(type, "경로 지점 역할이 필요합니다.");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("경로 지점 이름이 필요합니다.");
        }
        if (latitude == null
                || latitude.compareTo(MIN_LATITUDE) < 0
                || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("경로 지점 위도는 -90~90 범위여야 합니다.");
        }
        if (longitude == null
                || longitude.compareTo(MIN_LONGITUDE) < 0
                || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("경로 지점 경도는 -180~180 범위여야 합니다.");
        }
    }
}
