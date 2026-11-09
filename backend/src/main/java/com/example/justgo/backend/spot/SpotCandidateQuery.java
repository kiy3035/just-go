package com.example.justgo.backend.spot;

import java.math.BigDecimal;
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

    /**
     * 출발지에서 직선거리 상한 안에 있는 검수 완료 후보를 조회합니다.
     * 단순 테스트 대역은 기존 테마 조회로 대체할 수 있도록 기본 구현을 제공합니다.
     */
    default List<SpotCandidateRecord> loadNearbyVerifiedCandidates(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        return loadVerifiedCandidates(theme, limit);
    }

    /**
     * 후보 목록과 외부 원천의 부분 실패 정보를 함께 조회합니다.
     * 단순 구현체는 기존 후보 조회 결과를 완전 성공으로 간주합니다.
     */
    default SpotCandidateSearchResult searchNearbyCandidates(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        return new SpotCandidateSearchResult(
                loadNearbyVerifiedCandidates(
                        theme,
                        originLatitude,
                        originLongitude,
                        maxDistanceMeters,
                        limit),
                List.of());
    }

    /**
     * 검수 완료 후보가 하나 이상 준비된 풍경 테마를 조회합니다.
     * 간단한 테스트 대역은 이 기능을 구현하지 않아도 되도록 기본값을 빈 목록으로 둡니다.
     */
    default List<ScenicTheme> loadAvailableThemes() {
        return List.of();
    }
}
