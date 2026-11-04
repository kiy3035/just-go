package com.example.justgo.backend.spot;

import java.util.List;
import java.util.Objects;

/** 로컬 검수 파일의 최상위 JSON 구조입니다. */
public record SpotCandidateImportDocument(List<SpotCandidateImportEntry> candidates) {

    /** 파싱 이후 후보 목록이 외부에서 바뀌지 않도록 복사합니다. */
    public SpotCandidateImportDocument {
        if (candidates != null && candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates 배열에 빈 후보 객체를 둘 수 없습니다.");
        }
        candidates = candidates == null ? null : List.copyOf(candidates);
    }
}
