package com.example.justgo.backend.spot;

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
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidSpotLimitException(limit);
        }

        return List.copyOf(spotMapper.findVerifiedCandidatesByTheme(theme, limit));
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
