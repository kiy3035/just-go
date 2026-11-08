package com.example.justgo.backend.route;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;

/** 사용자가 경로 계산 전에 비교할 수 있는 단기 장소 후보입니다. */
public record CandidatePlaceResponse(
        String candidateToken,
        String name,
        ScenicTheme theme,
        BigDecimal latitude,
        BigDecimal longitude,
        long straightLineDistanceMeters,
        String source) {
}
