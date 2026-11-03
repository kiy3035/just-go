package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 장소 후보 서비스의 조회 제한과 DB 모델 변환을 검증합니다.
 */
class SpotServiceTest {

    private final SpotMapper spotMapper = mock(SpotMapper.class);
    private final SpotService spotService = new SpotService(spotMapper);

    /**
     * Mapper가 반환한 검수 후보를 외부 응답 DTO로 변환하는지 확인합니다.
     */
    @Test
    void returnsVerifiedCandidates() {
        when(spotMapper.findVerifiedCandidatesByTheme(ScenicTheme.SEA, 10))
                .thenReturn(List.of(new SpotCandidateRecord(
                        1L,
                        "VERIFIED_SPOT_TEST_ONLY",
                        ScenicTheme.SEA,
                        new BigDecimal("0.100000"),
                        new BigDecimal("0.200000"))));

        SpotCandidateListResponse response =
                spotService.findVerifiedCandidates(ScenicTheme.SEA, 10);

        assertThat(response.count()).isOne();
        assertThat(response.spots().getFirst().name()).isEqualTo("VERIFIED_SPOT_TEST_ONLY");
        assertThat(response.spots().getFirst().theme()).isEqualTo(ScenicTheme.SEA);
    }

    /**
     * 과도한 조회로 DB와 응답이 커지지 않도록 1~50 범위를 강제하는지 확인합니다.
     */
    @Test
    void rejectsInvalidLimit() {
        assertThatThrownBy(() -> spotService.findVerifiedCandidates(ScenicTheme.CITY, 51))
                .isInstanceOf(InvalidSpotLimitException.class)
                .hasMessageContaining("1 이상 50 이하");
    }

    /**
     * 추천 계층용 조회 경계도 공개 API와 같은 검수 완료 후보 목록을 반환하는지 확인합니다.
     */
    @Test
    void loadsVerifiedCandidatesForRecommendation() {
        SpotCandidateRecord candidate = new SpotCandidateRecord(
                2L,
                "VERIFIED_ROUTE_CANDIDATE_TEST_ONLY",
                ScenicTheme.MOUNTAIN,
                new BigDecimal("0.300000"),
                new BigDecimal("0.400000"));
        when(spotMapper.findVerifiedCandidatesByTheme(ScenicTheme.MOUNTAIN, 20))
                .thenReturn(List.of(candidate));

        assertThat(spotService.loadVerifiedCandidates(ScenicTheme.MOUNTAIN, 20))
                .containsExactly(candidate);
    }

    /** 후보가 준비된 테마를 화면의 확정 테마 순서로 정렬해 반환하는지 확인합니다. */
    @Test
    void loadsAvailableThemesInDefinedOrder() {
        when(spotMapper.findAvailableVerifiedThemes())
                .thenReturn(List.of(ScenicTheme.SEA, ScenicTheme.RIVER, ScenicTheme.CITY));

        assertThat(spotService.loadAvailableThemes())
                .containsExactly(ScenicTheme.CITY, ScenicTheme.RIVER, ScenicTheme.SEA);
    }
}
