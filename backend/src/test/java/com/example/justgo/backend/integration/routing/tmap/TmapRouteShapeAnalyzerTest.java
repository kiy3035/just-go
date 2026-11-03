package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 왕복 경로 선분의 중복 주행 비율 계산 경계를 검증합니다. */
class TmapRouteShapeAnalyzerTest {

    private final TmapRouteShapeAnalyzer analyzer = new TmapRouteShapeAnalyzer();

    /** 같은 선분을 반대 방향으로 돌아오면 최초 통과 이후 거리를 중복으로 계산합니다. */
    @Test
    void recognizesReverseDirectionAsRepeatedSegment() {
        Integer result = analyzer.calculateRepeatedSegmentPercent(List.of(
                List.of(point("0.000000", "0.000000"),
                        point("0.000000", "0.010000"),
                        point("0.000000", "0.020000")),
                List.of(point("0.000000", "0.020000"),
                        point("0.000000", "0.010000"),
                        point("0.000000", "0.000000"))));

        assertThat(result).isEqualTo(50);
    }

    /** 서로 다른 선분으로만 구성된 순환 경로는 중복률 0%로 계산합니다. */
    @Test
    void returnsZeroForUniqueSegments() {
        Integer result = analyzer.calculateRepeatedSegmentPercent(List.of(List.of(
                point("0.000000", "0.000000"),
                point("0.000000", "0.010000"),
                point("0.010000", "0.010000"),
                point("0.010000", "0.000000"),
                point("0.000000", "0.000000"))));

        assertThat(result).isZero();
    }

    /** 유효한 선분이 없을 때 품질 수치를 임의로 만들지 않습니다. */
    @Test
    void returnsNullWithoutUsableSegments() {
        assertThat(analyzer.calculateRepeatedSegmentPercent(List.of())).isNull();
        assertThat(analyzer.calculateRepeatedSegmentPercent(List.of(List.of(
                point("0.000000", "0.000000"))))).isNull();
    }

    /** 실제 위치와 무관한 테스트 전용 WGS84 좌표를 만듭니다. */
    private TmapCoordinate point(String latitude, String longitude) {
        return new TmapCoordinate(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
