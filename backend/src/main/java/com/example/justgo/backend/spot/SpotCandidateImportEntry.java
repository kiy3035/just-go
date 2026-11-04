package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 수동 검수를 마친 로컬 JSON 후보 한 건입니다.
 * 공개 API DTO가 아니며 candidate-import 프로필에서만 사용합니다.
 */
public record SpotCandidateImportEntry(
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude,
        String sourceName,
        String sourceReference,
        String usageRightsNote,
        OffsetDateTime verifiedAt,
        Integer themeFitScore,
        Integer scenicQualityScore,
        Integer routeSuitabilityScore,
        String qualityReviewNote) {
}
