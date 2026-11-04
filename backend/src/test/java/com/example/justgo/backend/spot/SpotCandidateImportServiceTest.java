package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 로컬 후보 가져오기의 선검증·좌표 정규화·중복 제외를 검증합니다. */
class SpotCandidateImportServiceTest {

    private final SpotMapper spotMapper = mock(SpotMapper.class);
    private final SpotCandidateImportService importService =
            new SpotCandidateImportService(spotMapper);

    /** 유효한 후보를 VERIFIED 등록값으로 바꾸고 좌표를 여섯 자리로 반올림합니다. */
    @Test
    void importsValidatedCandidate() {
        SpotCandidateImportEntry entry = validEntry(4);
        when(spotMapper.countCandidateIdentity(any(), any(), any())).thenReturn(0);

        SpotCandidateImportResult result = importService.importCandidates(List.of(entry));

        ArgumentCaptor<SpotCandidateRegistration> captor =
                ArgumentCaptor.forClass(SpotCandidateRegistration.class);
        verify(spotMapper).insertCandidate(captor.capture());
        SpotCandidateRegistration registration = captor.getValue();
        assertThat(result.insertedCount()).isOne();
        assertThat(result.skippedDuplicateCount()).isZero();
        assertThat(registration.verificationStatus()).isEqualTo(SpotVerificationStatus.VERIFIED);
        assertThat(registration.latitude()).isEqualByComparingTo("0.123457");
        assertThat(registration.longitude()).isEqualByComparingTo("0.234568");
    }

    /** 같은 이름·출처·참조 조합이 있으면 다시 저장하지 않습니다. */
    @Test
    void skipsExistingCandidateIdentity() {
        when(spotMapper.countCandidateIdentity(any(), any(), any())).thenReturn(1);

        SpotCandidateImportResult result = importService.importCandidates(List.of(validEntry(3)));

        assertThat(result.insertedCount()).isZero();
        assertThat(result.skippedDuplicateCount()).isOne();
        verify(spotMapper).countCandidateIdentity(any(), any(), any());
        verify(spotMapper, never()).insertCandidate(any());
    }

    /** 한 건이라도 품질 기준을 위반하면 DB 작업 전에 전체 가져오기를 중단합니다. */
    @Test
    void rejectsWholeBatchBeforeDatabaseWork() {
        List<SpotCandidateImportEntry> entries = List.of(validEntry(3), validEntry(2));

        assertThatThrownBy(() -> importService.importCandidates(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidates[1]", "themeFitScore");
        verifyNoInteractions(spotMapper);
    }

    /** 사전 검증은 DB를 변경하지 않고 파일 안 중복을 한 번만 조회합니다. */
    @Test
    void validatesWithoutWritingAndCountsInputDuplicate() {
        SpotCandidateImportEntry entry = validEntry(4);
        when(spotMapper.countCandidateIdentity(any(), any(), any())).thenReturn(0);

        SpotCandidateImportValidationResult result =
                importService.validateCandidates(List.of(entry, entry));

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.readyCount()).isOne();
        assertThat(result.existingDuplicateCount()).isZero();
        assertThat(result.inputDuplicateCount()).isOne();
        verify(spotMapper, times(1)).countCandidateIdentity(any(), any(), any());
        verify(spotMapper, never()).insertCandidate(any());
    }

    /** 사전 검증은 DB에 이미 있는 후보를 등록 가능 건수에서 제외합니다. */
    @Test
    void reportsExistingCandidateDuringValidation() {
        when(spotMapper.countCandidateIdentity(any(), any(), any())).thenReturn(1);

        SpotCandidateImportValidationResult result =
                importService.validateCandidates(List.of(validEntry(3)));

        assertThat(result.readyCount()).isZero();
        assertThat(result.existingDuplicateCount()).isOne();
        assertThat(result.inputDuplicateCount()).isZero();
        verify(spotMapper, never()).insertCandidate(any());
    }

    /** 실제 장소와 혼동되지 않는 가짜 출처·좌표의 테스트 후보를 만듭니다. */
    private SpotCandidateImportEntry validEntry(int themeFitScore) {
        return new SpotCandidateImportEntry(
                "IMPORT_TEST_ONLY",
                ScenicTheme.SEA,
                new BigDecimal("0.1234567"),
                new BigDecimal("0.2345678"),
                "SOURCE_TEST_ONLY",
                "https://example.invalid/import-test-only",
                "USAGE_RIGHTS_TEST_ONLY",
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                themeFitScore,
                4,
                5,
                "QUALITY_REVIEW_TEST_ONLY");
    }
}
