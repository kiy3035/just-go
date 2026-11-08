package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검수 완료 경관 장소 후보를 조회하고 API 응답으로 변환합니다.
 */
@Service
public class SpotService implements SpotCandidateQuery {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final SpotMapper spotMapper;

    public SpotService(SpotMapper spotMapper) {
        this.spotMapper = spotMapper;
    }

    /**
     * 지정한 테마에서 활성화된 검수 완료 장소만 제한된 개수로 조회합니다.
     *
     * @param theme 확정된 풍경 테마
     * @param limit 최대 조회 건수(1~50)
     * @return 테마와 조회 건수가 포함된 장소 목록
     */
    @Transactional(readOnly = true)
    public SpotCandidateListResponse findVerifiedCandidates(ScenicTheme theme, int limit) {
        List<SpotCandidateResponse> spots = loadVerifiedCandidates(theme, limit)
                .stream()
                .map(this::toResponse)
                .toList();
        return new SpotCandidateListResponse(theme, spots.size(), spots);
    }

    /**
     * 추천 서비스와 공개 조회 API가 같은 검수 조건을 사용하도록 Mapper 조회를 한곳에 둡니다.
     * 이 메서드가 끝난 뒤 외부 경로 API를 호출하므로 DB 트랜잭션을 외부 I/O 동안 유지하지 않습니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SpotCandidateRecord> loadVerifiedCandidates(ScenicTheme theme, int limit) {
        validateLimit(limit);

        return List.copyOf(spotMapper.findVerifiedCandidatesByTheme(theme, limit));
    }

    /** 출발 좌표를 저장하지 않고 직선거리 상한 안의 후보만 추천 계층에 전달합니다. */
    @Override
    @Transactional(readOnly = true)
    public List<SpotCandidateRecord> loadNearbyVerifiedCandidates(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        validateLimit(limit);
        validateCoordinate(originLatitude, -90, 90, "위도");
        validateCoordinate(originLongitude, -180, 180, "경도");
        if (maxDistanceMeters <= 0) {
            throw new IllegalArgumentException("후보 직선거리 상한은 0m보다 커야 합니다.");
        }
        return List.copyOf(spotMapper.findNearbyVerifiedCandidatesByTheme(
                theme,
                originLatitude,
                originLongitude,
                maxDistanceMeters,
                limit));
    }

    /** 외부 후보보다 먼저 적용할 수동 분류 보정·제외 규칙을 반환합니다. */
    @Transactional(readOnly = true)
    public List<SpotCandidateOverrideRecord> loadCandidateOverrides() {
        return List.copyOf(spotMapper.findCandidateOverrides());
    }

    /** 검수 후보가 실제로 존재하는 테마를 확정된 화면 표시 순서로 반환합니다. */
    @Override
    @Transactional(readOnly = true)
    public List<ScenicTheme> loadAvailableThemes() {
        return spotMapper.findAvailableVerifiedThemes().stream()
                .sorted(Comparator.comparingInt(ScenicTheme::ordinal))
                .toList();
    }

    /** 공개 조회와 추천 조회에 같은 1~50건 제한을 적용합니다. */
    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidSpotLimitException(limit);
        }
    }

    /** DB 거리 계산 전에 WGS84 좌표 범위를 다시 확인합니다. */
    private void validateCoordinate(
            BigDecimal coordinate,
            int minimum,
            int maximum,
            String label) {
        if (coordinate == null
                || coordinate.compareTo(BigDecimal.valueOf(minimum)) < 0
                || coordinate.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException(label + " WGS84 범위를 확인해 주세요.");
        }
    }

    /**
     * DB 모델을 외부 API DTO로 분리해 변환합니다.
     */
    private SpotCandidateResponse toResponse(SpotCandidateRecord record) {
        return new SpotCandidateResponse(
                record.id(),
                record.name(),
                record.theme(),
                record.latitude(),
                record.longitude());
    }
}
