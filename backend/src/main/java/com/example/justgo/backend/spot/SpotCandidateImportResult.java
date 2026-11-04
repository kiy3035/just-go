package com.example.justgo.backend.spot;

/** 로컬 후보 가져오기에서 새로 저장한 건수와 중복으로 건너뛴 건수입니다. */
public record SpotCandidateImportResult(int insertedCount, int skippedDuplicateCount) {
}
