package com.example.justgo.backend.integration.routing.tmap;

import java.math.BigDecimal;

/**
 * TMAP 요청에 사용하는 WGS84 좌표입니다.
 * X축은 경도, Y축은 위도라는 외부 API 규칙과 혼동하지 않도록 값의 의미를 명시합니다.
 */
public record TmapCoordinate(BigDecimal latitude, BigDecimal longitude) {

    /**
     * 외부 API 호출 전에 null과 WGS84 좌표 범위를 검증합니다.
     */
    public TmapCoordinate {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("TMAP 좌표는 위도와 경도가 모두 필요합니다.");
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("TMAP 위도는 -90 이상 90 이하여야 합니다.");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("TMAP 경도는 -180 이상 180 이하여야 합니다.");
        }
    }
}
