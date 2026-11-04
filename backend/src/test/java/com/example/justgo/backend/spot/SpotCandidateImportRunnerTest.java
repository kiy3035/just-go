package com.example.justgo.backend.spot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 후보 가져오기 실행기가 dry-run에서 저장 경로를 호출하지 않는지 검증합니다. */
class SpotCandidateImportRunnerTest {

    /** dry-run은 파일을 읽고 사전 검증만 수행하며 실제 가져오기는 실행하지 않습니다. */
    @Test
    void dryRunValidatesWithoutImporting() {
        SpotCandidateImportFileReader fileReader = mock(SpotCandidateImportFileReader.class);
        SpotCandidateImportService importService = mock(SpotCandidateImportService.class);
        SpotCandidateImportDocument document = new SpotCandidateImportDocument(List.of());
        when(fileReader.read(any(Path.class))).thenReturn(document);
        when(importService.validateCandidates(document.candidates()))
                .thenReturn(new SpotCandidateImportValidationResult(2, 1, 0, 1));
        SpotCandidateImportRunner runner =
                new SpotCandidateImportRunner(
                        fileReader, importService, "candidate-test-only.json", true);

        runner.run(null);

        verify(importService).validateCandidates(document.candidates());
        verify(importService, never()).importCandidates(any());
    }
}
