package com.example.justgo.backend.spot;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검수 완료 경관 장소 후보를 조회하고 API 응답으로 변환합니다.
 */
@Service
public class SpotService {

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
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidSpotLimitException(limit);
        }

        List<SpotCandidateResponse> spots = spotMapper.findVerifiedCandidatesByTheme(theme, limit)
                .stream()
                .map(this::toResponse)
                .toList();
        return new SpotCandidateListResponse(theme, spots.size(), spots);
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
