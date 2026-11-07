package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 검수된 로컬 후보를 전부 검증한 뒤 한 트랜잭션으로 등록합니다.
 * 일부만 저장되는 상황을 막기 위해 DB 작업 전에 전체 입력을 먼저 변환합니다.
 */
@Service
@Profile("candidate-import")
public class SpotCandidateImportService {

    private static final int MAX_CANDIDATES = 500;

    private final SpotMapper spotMapper;

    public SpotCandidateImportService(SpotMapper spotMapper) {
        this.spotMapper = spotMapper;
    }

    /** 검수 완료 후보만 저장하고 같은 이름·출처·참조 조합은 중복으로 건너뜁니다. */
    @Transactional
    public SpotCandidateImportResult importCandidates(List<SpotCandidateImportEntry> entries) {
        PreparedCandidates prepared = prepare(entries);
        int insertedCount = 0;
        int skippedDuplicateCount = prepared.inputDuplicateCount();
        for (SpotCandidateRegistration registration : prepared.uniqueRegistrations()) {
            if (exists(registration)) {
                skippedDuplicateCount++;
                continue;
            }
            spotMapper.insertCandidate(registration);
            insertedCount++;
        }
        return new SpotCandidateImportResult(insertedCount, skippedDuplicateCount);
    }

    /** DB를 변경하지 않고 전체 입력의 형식과 기존·파일 내 중복 건수를 확인합니다. */
    @Transactional(readOnly = true)
    public SpotCandidateImportValidationResult validateCandidates(
            List<SpotCandidateImportEntry> entries) {
        PreparedCandidates prepared = prepare(entries);
        int existingDuplicateCount = 0;
        for (SpotCandidateRegistration registration : prepared.uniqueRegistrations()) {
            if (exists(registration)) {
                existingDuplicateCount++;
            }
        }
        int readyCount = prepared.uniqueRegistrations().size() - existingDuplicateCount;
        return new SpotCandidateImportValidationResult(
                prepared.totalCount(),
                readyCount,
                existingDuplicateCount,
                prepared.inputDuplicateCount());
    }

    /** 모든 행을 먼저 검증하고 파일 안의 같은 후보를 한 번만 처리하도록 묶습니다. */
    private PreparedCandidates prepare(List<SpotCandidateImportEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("가져올 경관 후보가 한 건 이상 필요합니다.");
        }
        if (entries.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("경관 후보는 한 번에 500건까지만 가져올 수 있습니다.");
        }

        List<SpotCandidateRegistration> registrations = IntStream
                .range(0, entries.size())
                .mapToObj(index -> toRegistration(entries.get(index), index))
                .toList();

        Map<CandidateIdentity, SpotCandidateRegistration> uniqueRegistrations =
                new LinkedHashMap<>();
        for (SpotCandidateRegistration registration : registrations) {
            uniqueRegistrations.putIfAbsent(CandidateIdentity.from(registration), registration);
        }
        return new PreparedCandidates(
                registrations.size(),
                List.copyOf(uniqueRegistrations.values()),
                registrations.size() - uniqueRegistrations.size());
    }

    /** 후보 원문을 노출하지 않고 동일성 조회 결과만 반환합니다. */
    private boolean exists(SpotCandidateRegistration registration) {
        return spotMapper.countCandidateIdentity(
                        registration.name(),
                        registration.sourceName(),
                        registration.sourceReference())
                > 0;
    }

    /** JSON 한 건을 DB 길이·좌표·품질 규칙에 맞는 VERIFIED 등록값으로 변환합니다. */
    private SpotCandidateRegistration toRegistration(SpotCandidateImportEntry entry, int index) {
        if (entry == null) {
            throw invalid(index, "후보 객체가 필요합니다.");
        }
        String name = required(entry.name(), 100, index, "name");
        if (entry.theme() == null) {
            throw invalid(index, "theme이 필요합니다.");
        }
        if (entry.theme() == ScenicTheme.ANYWHERE || entry.theme() == ScenicTheme.WATER) {
            throw invalid(index, "장소에는 실제 테마를 지정해야 합니다.");
        }
        BigDecimal latitude = coordinate(entry.latitude(), -90, 90, index, "latitude");
        BigDecimal longitude = coordinate(entry.longitude(), -180, 180, index, "longitude");
        String sourceName = required(entry.sourceName(), 200, index, "sourceName");
        String sourceReference = required(
                entry.sourceReference(), 1000, index, "sourceReference");
        String usageRightsNote = required(
                entry.usageRightsNote(), 500, index, "usageRightsNote");
        if (entry.verifiedAt() == null) {
            throw invalid(index, "verifiedAt이 필요합니다.");
        }
        int themeFitScore = qualityScore(entry.themeFitScore(), index, "themeFitScore");
        int scenicQualityScore = qualityScore(
                entry.scenicQualityScore(), index, "scenicQualityScore");
        int routeSuitabilityScore = qualityScore(
                entry.routeSuitabilityScore(), index, "routeSuitabilityScore");
        String qualityReviewNote = required(
                entry.qualityReviewNote(), 500, index, "qualityReviewNote");

        return new SpotCandidateRegistration(
                name,
                entry.theme(),
                latitude,
                longitude,
                SpotVerificationStatus.VERIFIED,
                true,
                sourceName,
                sourceReference,
                usageRightsNote,
                entry.verifiedAt(),
                themeFitScore,
                scenicQualityScore,
                routeSuitabilityScore,
                qualityReviewNote);
    }

    /** 공백을 제거한 필수 문자열이 DB 최대 길이 안인지 확인합니다. */
    private String required(String value, int maxLength, int index, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalid(index, fieldName + "이(가) 필요합니다.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid(index, fieldName + " 길이가 " + maxLength + "자를 초과했습니다.");
        }
        return normalized;
    }

    /** WGS84 범위를 확인하고 DB 좌표 정밀도인 소수점 여섯 자리로 반올림합니다. */
    private BigDecimal coordinate(
            BigDecimal value,
            int minimum,
            int maximum,
            int index,
            String fieldName) {
        if (value == null
                || value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw invalid(index, fieldName + " WGS84 범위를 확인해 주세요.");
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    /** 추천에 바로 쓰는 VERIFIED 후보이므로 모든 품질 점수가 3~5점인지 확인합니다. */
    private int qualityScore(Integer value, int index, String fieldName) {
        if (value == null || value < 3 || value > 5) {
            throw invalid(index, fieldName + "은(는) 3~5점이어야 합니다.");
        }
        return value;
    }

    /** 실제 후보값을 오류 메시지에 노출하지 않고 배열 위치와 필드만 알립니다. */
    private IllegalArgumentException invalid(int index, String message) {
        return new IllegalArgumentException("candidates[" + index + "]: " + message);
    }

    /** 후보 중복 판정에 사용하는 이름·출처·참조 조합입니다. */
    private record CandidateIdentity(String name, String sourceName, String sourceReference) {

        private static CandidateIdentity from(SpotCandidateRegistration registration) {
            return new CandidateIdentity(
                    registration.name(),
                    registration.sourceName(),
                    registration.sourceReference());
        }
    }

    /** 전체 검증을 마친 고유 후보와 파일 내 중복 건수를 함께 보관합니다. */
    private record PreparedCandidates(
            int totalCount,
            List<SpotCandidateRegistration> uniqueRegistrations,
            int inputDuplicateCount) {
    }
}
