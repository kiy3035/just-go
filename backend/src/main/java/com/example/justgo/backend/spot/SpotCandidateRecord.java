package com.example.justgo.backend.spot;

import java.math.BigDecimal;

/**
 * 추천 후보 조회에 필요한 검수 완료 장소의 DB 모델입니다.
 * 좌표는 WGS84 위도·경도를 사용하며 API DTO와 분리합니다.
 */
public record SpotCandidateRecord(
        Long id,
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude) {
}
