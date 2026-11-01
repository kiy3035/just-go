package com.example.justgo.backend.spot;

import java.util.List;

/**
 * 추천 계층이 검수 완료 장소 후보를 조회할 때 사용하는 읽기 전용 경계입니다.
 * 추천 로직이 MyBatis 구현 세부사항에 직접 의존하지 않도록 분리합니다.
 */
public interface SpotCandidateQuery {

    /**
     * 지정한 테마에서 활성화된 검수 완료 장소만 제한된 개수로 조회합니다.
     *
     * @param theme 풍경 테마
     * @param limit 최대 조회 건수
     * @return 추천에 전달할 검수 완료 장소 후보
     */
    List<SpotCandidateRecord> loadVerifiedCandidates(ScenicTheme theme, int limit);
}
