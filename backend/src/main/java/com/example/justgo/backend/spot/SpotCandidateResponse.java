package com.example.justgo.backend.spot;

import java.math.BigDecimal;

/**
 * 검수 완료 경관 장소 한 건을 클라이언트에 전달하는 응답 DTO입니다.
 */
public record SpotCandidateResponse(
        Long id,
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude) {
}
