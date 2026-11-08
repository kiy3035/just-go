package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** 검수 DB와 활성화된 공공데이터 원천을 합치고 중복·거리·개수를 제한합니다. */
@Service
@Primary
public class CompositeSpotCandidateQuery implements SpotCandidateQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeSpotCandidateQuery.class);
    private static final int SOURCE_FETCH_MULTIPLIER = 3;

    private final SpotService verifiedSpotService;
    private final List<ScenicPlaceSource> externalSources;

    public CompositeSpotCandidateQuery(
            SpotService verifiedSpotService,
            List<ScenicPlaceSource> externalSources) {
        this.verifiedSpotService = verifiedSpotService;
        this.externalSources = List.copyOf(externalSources);
    }

    @Override
    public List<SpotCandidateRecord> loadVerifiedCandidates(ScenicTheme theme, int limit) {
        return verifiedSpotService.loadVerifiedCandidates(theme, limit);
    }

    @Override
    public List<SpotCandidateRecord> loadNearbyVerifiedCandidates(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        List<SpotCandidateRecord> combined = new ArrayList<>(
                verifiedSpotService.loadNearbyVerifiedCandidates(
                        theme, originLatitude, originLongitude, maxDistanceMeters, limit));
        int sourceLimit = Math.min(50, Math.max(limit, limit * SOURCE_FETCH_MULTIPLIER));
        for (ScenicPlaceSource source : externalSources) {
            if (!supports(source.supportedThemes(), theme)) {
                continue;
            }
            try {
                combined.addAll(source.findNearby(
                        theme,
                        originLatitude,
                        originLongitude,
                        maxDistanceMeters,
                        sourceLimit));
            } catch (RuntimeException exception) {
                LOGGER.warn("외부 장소 후보 조회 실패: source={}", source.sourceName());
            }
        }

        LinkedHashMap<String, SpotCandidateRecord> unique = new LinkedHashMap<>();
        combined.stream()
                .filter(candidate -> candidate != null && theme.includes(candidate.theme()))
                .filter(this::hasValidCoordinate)
                .filter(candidate -> ScenicPlaceDistance.meters(
                        originLatitude,
                        originLongitude,
                        candidate.latitude(),
                        candidate.longitude()) <= maxDistanceMeters)
                .sorted(Comparator
                        .comparingInt(this::sourcePriority)
                        .thenComparingLong(candidate -> ScenicPlaceDistance.meters(
                                originLatitude,
                                originLongitude,
                                candidate.latitude(),
                                candidate.longitude()))
                        .thenComparing(SpotCandidateRecord::name)
                        .thenComparing(SpotCandidateRecord::id))
                .forEach(candidate -> unique.putIfAbsent(deduplicationKey(candidate), candidate));
        return unique.values().stream().limit(limit).toList();
    }

    @Override
    public List<ScenicTheme> loadAvailableThemes() {
        Set<ScenicTheme> themes = new LinkedHashSet<>(verifiedSpotService.loadAvailableThemes());
        externalSources.forEach(source -> themes.addAll(source.supportedThemes()));
        return themes.stream()
                .filter(theme -> theme != ScenicTheme.ANYWHERE && theme != ScenicTheme.WATER)
                .sorted(Comparator.comparingInt(ScenicTheme::ordinal))
                .toList();
    }

    private boolean supports(Set<ScenicTheme> supportedThemes, ScenicTheme requestedTheme) {
        return requestedTheme == ScenicTheme.ANYWHERE
                || supportedThemes.stream().anyMatch(requestedTheme::includes);
    }

    private boolean hasValidCoordinate(SpotCandidateRecord candidate) {
        return candidate.id() != null
                && candidate.name() != null
                && !candidate.name().isBlank()
                && candidate.theme() != null
                && candidate.latitude() != null
                && candidate.longitude() != null
                && candidate.latitude().compareTo(BigDecimal.valueOf(-90)) >= 0
                && candidate.latitude().compareTo(BigDecimal.valueOf(90)) <= 0
                && candidate.longitude().compareTo(BigDecimal.valueOf(-180)) >= 0
                && candidate.longitude().compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private int sourcePriority(SpotCandidateRecord candidate) {
        return "VERIFIED_DB".equals(candidate.source()) ? 0 : 1;
    }

    private String deduplicationKey(SpotCandidateRecord candidate) {
        return candidate.name().replaceAll("[^가-힣a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT)
                + '|'
                + candidate.latitude().setScale(4, RoundingMode.HALF_UP)
                + '|'
                + candidate.longitude().setScale(4, RoundingMode.HALF_UP);
    }
}
