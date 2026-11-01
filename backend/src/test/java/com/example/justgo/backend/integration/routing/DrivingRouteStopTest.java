package com.example.justgo.backend.integration.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 내비 전달 지점이 잘못된 순서나 WGS84 범위를 허용하지 않는지 검증합니다. */
class DrivingRouteStopTest {

    /** 0 이하 순서가 내비 전달 목록에 들어가지 않도록 차단합니다. */
    @Test
    void rejectsNonPositiveSequence() {
        assertThatThrownBy(() -> new DrivingRouteStop(
                0,
                DrivingRouteStopType.WAYPOINT,
                "INVALID_SEQUENCE_TEST_ONLY",
                BigDecimal.ZERO,
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("순서");
    }

    /** WGS84 범위를 벗어난 위도를 외부 API 호출 전에 차단합니다. */
    @Test
    void rejectsLatitudeOutsideWgs84Range() {
        assertThatThrownBy(() -> new DrivingRouteStop(
                1,
                DrivingRouteStopType.DESTINATION,
                "INVALID_COORDINATE_TEST_ONLY",
                new BigDecimal("90.000001"),
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("위도");
    }
}
