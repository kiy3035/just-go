package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** 외부 장소 원천을 추천 계층과 분리하는 읽기 전용 경계입니다. */
public interface ScenicPlaceSource {

    String sourceName();

    Set<ScenicTheme> supportedThemes();

    List<SpotCandidateRecord> findNearby(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit);

    /** 자동 추천의 거리 구간 선별 전에, 원천의 기존 페이지 제한 안에서 후보를 제공한다. */
    default List<SpotCandidateRecord> findForRecommendation(
            ScenicTheme theme, BigDecimal latitude, BigDecimal longitude, long radiusMeters) {
        return findNearby(theme, latitude, longitude, radiusMeters, Integer.MAX_VALUE);
    }
}
