package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

        int insertedCount = 0;
        int skippedDuplicateCount = 0;
        for (SpotCandidateRegistration registration : registrations) {
            int existingCount = spotMapper.countCandidateIdentity(
                    registration.name(),
                    registration.sourceName(),
                    registration.sourceReference());
            if (existingCount > 0) {
                skippedDuplicateCount++;
                continue;
            }
            spotMapper.insertCandidate(registration);
            insertedCount++;
        }
        return new SpotCandidateImportResult(insertedCount, skippedDuplicateCount);
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
}
