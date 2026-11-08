package com.example.justgo.backend.spot;

import java.math.BigDecimal;

/**
 * 추천 후보 조회에 필요한 정규화된 장소 모델입니다.
 * DB 검수 후보와 단기 사용 공공데이터 후보를 같은 경계에서 다루며 좌표는 WGS84입니다.
 */
public record SpotCandidateRecord(
        Long id,
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude,
        String source) {

    /** 기존 DB Mapper와 테스트는 명시적으로 검수 DB 출처를 사용합니다. */
    public SpotCandidateRecord(
            Long id,
            String name,
            ScenicTheme theme,
            BigDecimal latitude,
            BigDecimal longitude) {
        this(id, name, theme, latitude, longitude, "VERIFIED_DB");
    }
}
