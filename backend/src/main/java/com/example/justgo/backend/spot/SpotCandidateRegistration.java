package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 경관 후보를 DB에 등록할 때 필요한 좌표·검수 상태·출처 이력을 묶습니다.
 * 공개 쓰기 API에는 노출하지 않으며 VERIFIED 후보는 DB 제약으로 출처 이력을 강제합니다.
 */
public record SpotCandidateRegistration(
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude,
        SpotVerificationStatus verificationStatus,
        boolean enabled,
        String sourceName,
        String sourceReference,
        String usageRightsNote,
        OffsetDateTime verifiedAt) {
}
