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
}
