package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 원천 후보 우선, 수동 보정 중복, ANYWHERE 테마 다양성을 검증합니다. */
class CompositeSpotCandidateQueryTest {

    @Test
    void anywhereAlternatesThemesAndKeepsLandmarkPriority() {
        SpotService verifiedSpotService = mock(SpotService.class);
        when(verifiedSpotService.loadNearbyVerifiedCandidates(
                any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(new SpotCandidateRecord(
                        1L,
                        "CURATED_DUPLICATE_TEST_ONLY",
                        ScenicTheme.CITY,
                        new BigDecimal("37.5050"),
                        new BigDecimal("127.0000"))));
        when(verifiedSpotService.loadCandidateOverrides()).thenReturn(List.of(
                new SpotCandidateOverrideRecord(
                        "CURATED_DUPLICATE_TEST_ONLY", ScenicTheme.CITY, true),
                new SpotCandidateOverrideRecord(
                        "EXCLUDED_TEST_ONLY", ScenicTheme.CITY, false)));

        List<SpotCandidateRecord> externalCandidates = new ArrayList<>();
        externalCandidates.add(candidate(
                -1L, "LANDMARK_TEST_ONLY", ScenicTheme.CITY, "37.5100", 0));
        externalCandidates.add(candidate(
                -2L, "GENERAL_FACILITY_TEST_ONLY", ScenicTheme.CITY, "37.5001", 5));
        externalCandidates.add(candidate(
                -3L, "CURATED_DUPLICATE_TEST_ONLY", ScenicTheme.CITY, "37.5060", 0));
        externalCandidates.add(candidate(
                -4L, "EXCLUDED_TEST_ONLY", ScenicTheme.CITY, "37.5070", 0));
        for (int index = 0; index < 10; index++) {
            externalCandidates.add(candidate(
                    -10L - index,
                    "PARK_TEST_ONLY_" + index,
                    ScenicTheme.PARK,
                    "37.50" + String.format("%02d", index + 1),
                    0));
        }

        ScenicPlaceSource source = new ScenicPlaceSource() {
            @Override
            public String sourceName() {
                return "TEST_SOURCE";
            }

            @Override
            public Set<ScenicTheme> supportedThemes() {
                return Set.of(ScenicTheme.CITY, ScenicTheme.PARK);
            }

            @Override
            public List<SpotCandidateRecord> findNearby(
                    ScenicTheme theme,
                    BigDecimal originLatitude,
                    BigDecimal originLongitude,
                    long maxDistanceMeters,
                    int limit) {
                return externalCandidates;
            }
        };
        CompositeSpotCandidateQuery query = new CompositeSpotCandidateQuery(
                verifiedSpotService,
                List.of(source));

        List<SpotCandidateRecord> result = query.loadNearbyVerifiedCandidates(
                ScenicTheme.ANYWHERE,
                new BigDecimal("37.5000"),
                new BigDecimal("127.0000"),
                100_000,
                6);

        assertThat(result).hasSize(6);
        assertThat(result.get(0).name()).isEqualTo("LANDMARK_TEST_ONLY");
        assertThat(result.stream().filter(candidate -> candidate.theme() == ScenicTheme.CITY))
                .hasSize(3);
        assertThat(result.stream().filter(candidate -> candidate.theme() == ScenicTheme.PARK))
                .hasSize(3);
        assertThat(result.stream()
                .filter(candidate -> candidate.name().equals("CURATED_DUPLICATE_TEST_ONLY")))
                .singleElement()
                .extracting(SpotCandidateRecord::source)
                .isEqualTo("VERIFIED_DB");
        assertThat(result).extracting(SpotCandidateRecord::name)
                .containsSubsequence("LANDMARK_TEST_ONLY", "GENERAL_FACILITY_TEST_ONLY");
        assertThat(result).extracting(SpotCandidateRecord::name)
                .doesNotContain("EXCLUDED_TEST_ONLY");
    }

    @Test
    void correctedThemeRemovesExternalCandidateFromOriginalTheme() {
        SpotService verifiedSpotService = mock(SpotService.class);
        when(verifiedSpotService.loadNearbyVerifiedCandidates(
                any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(verifiedSpotService.loadCandidateOverrides()).thenReturn(List.of(
                new SpotCandidateOverrideRecord(
                        "SOURCE_CATEGORY_CORRECTION_TEST_ONLY", ScenicTheme.PARK, true)));

        ScenicPlaceSource source = new ScenicPlaceSource() {
            @Override
            public String sourceName() {
                return "TEST_SOURCE";
            }

            @Override
            public Set<ScenicTheme> supportedThemes() {
                return Set.of(ScenicTheme.LAKE);
            }

            @Override
            public List<SpotCandidateRecord> findNearby(
                    ScenicTheme theme,
                    BigDecimal originLatitude,
                    BigDecimal originLongitude,
                    long maxDistanceMeters,
                    int limit) {
                return List.of(candidate(
                        -1L,
                        "SOURCE_CATEGORY_CORRECTION_TEST_ONLY",
                        ScenicTheme.LAKE,
                        "37.5100",
                        0));
            }
        };

        CompositeSpotCandidateQuery query = new CompositeSpotCandidateQuery(
                verifiedSpotService,
                List.of(source));

        assertThat(query.loadNearbyVerifiedCandidates(
                ScenicTheme.LAKE,
                new BigDecimal("37.5000"),
                new BigDecimal("127.0000"),
                100_000,
                10)).isEmpty();
    }

    private SpotCandidateRecord candidate(
            long id,
            String name,
            ScenicTheme theme,
            String latitude,
            int selectionPriority) {
        return new SpotCandidateRecord(
                id,
                name,
                theme,
                new BigDecimal(latitude),
                new BigDecimal("127.0000"),
                "TEST_SOURCE",
                selectionPriority);
    }
}
