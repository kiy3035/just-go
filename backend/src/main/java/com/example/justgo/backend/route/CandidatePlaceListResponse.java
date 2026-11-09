package com.example.justgo.backend.route;

import java.util.List;

import com.example.justgo.backend.spot.ScenicTheme;

/** 후보 토큰의 유효시간과 조회 목록을 함께 반환합니다. */
public record CandidatePlaceListResponse(
        ScenicTheme theme,
        int count,
        int tokenExpiresInMinutes,
        boolean partial,
        List<String> unavailableSources,
        List<CandidatePlaceResponse> candidates) {

    public CandidatePlaceListResponse {
        unavailableSources = List.copyOf(unavailableSources);
        candidates = List.copyOf(candidates);
    }
}
