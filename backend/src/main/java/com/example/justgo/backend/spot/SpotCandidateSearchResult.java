package com.example.justgo.backend.spot;

import java.util.List;

/** 외부 원천의 부분 실패 상태를 포함한 장소 후보 조회 결과입니다. */
public record SpotCandidateSearchResult(
        List<SpotCandidateRecord> candidates,
        List<String> unavailableSources) {

    public SpotCandidateSearchResult {
        candidates = List.copyOf(candidates);
        unavailableSources = List.copyOf(unavailableSources);
    }

    public boolean partial() {
        return !unavailableSources.isEmpty();
    }
}
