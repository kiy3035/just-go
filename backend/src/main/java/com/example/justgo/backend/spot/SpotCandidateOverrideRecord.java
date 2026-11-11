package com.example.justgo.backend.spot;

import java.math.BigDecimal;

/** 외부 후보의 분류를 보정하거나 같은 장소를 제외하는 수동 검수 규칙입니다. */
public record SpotCandidateOverrideRecord(
        String name,
        ScenicTheme theme,
        boolean enabled,
        BigDecimal latitude,
        BigDecimal longitude) {
}
