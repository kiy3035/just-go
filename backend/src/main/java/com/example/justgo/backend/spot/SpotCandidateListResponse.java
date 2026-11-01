package com.example.justgo.backend.spot;

import java.util.List;

/**
 * 테마별 검수 장소 조회 결과와 건수를 함께 반환합니다.
 */
public record SpotCandidateListResponse(
        ScenicTheme theme,
        int count,
        List<SpotCandidateResponse> spots) {

    /**
     * 응답 생성 이후 장소 목록이 변경되지 않도록 방어적으로 복사합니다.
     */
    public SpotCandidateListResponse {
        spots = List.copyOf(spots);
    }
}
