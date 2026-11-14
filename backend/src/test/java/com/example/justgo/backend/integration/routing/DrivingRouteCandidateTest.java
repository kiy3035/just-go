package com.example.justgo.backend.integration.routing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;

class DrivingRouteCandidateTest {

    @Test
    void acceptsMeasuredSecondsAndMatchingLegs() {
        assertThatCode(() -> candidate(60, 3_590L, List.of(3_590L)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInconsistentMinuteAndLegContracts() {
        assertThatThrownBy(() -> candidate(59, 3_600L, List.of(3_600L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(60, 3_600L, List.of(3_599L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> candidate(60, 3_600L, List.of()))
                .doesNotThrowAnyException();
    }

    private DrivingRouteCandidate candidate(int minutes, long seconds, List<Long> legs) {
        return new DrivingRouteCandidate(
                "TIME_CONTRACT_TEST",
                "TIME_CONTRACT_TEST",
                minutes,
                1_000,
                ScenicTheme.PARK,
                null,
                List.of(new DrivingRouteStop(
                        1,
                        DrivingRouteStopType.DESTINATION,
                        "TIME_CONTRACT_TEST",
                        new BigDecimal("37.500000"),
                        new BigDecimal("127.000000"))),
                "TIME_CONTRACT_TEST",
                seconds,
                legs);
    }
}
