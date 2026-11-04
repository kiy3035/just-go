package com.example.justgo.backend.spot;

/** DB 저장 전 후보 파일의 등록 가능·중복 건수를 실제 값 노출 없이 요약합니다. */
public record SpotCandidateImportValidationResult(
        int totalCount,
        int readyCount,
        int existingDuplicateCount,
        int inputDuplicateCount) {
}
