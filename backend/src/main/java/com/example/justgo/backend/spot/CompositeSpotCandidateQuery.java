package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final List<ScenicTheme> ACTUAL_THEME_ORDER = List.of(
            ScenicTheme.CITY,
            ScenicTheme.MOUNTAIN,
            ScenicTheme.RIVER,
            ScenicTheme.LAKE,
            ScenicTheme.SEA,
            ScenicTheme.PARK);

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
        Map<String, SpotCandidateOverrideRecord> overrides = verifiedSpotService
                .loadCandidateOverrides()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        override -> normalizedName(override.name()),
                        override -> override,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
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
                .filter(candidate -> isAllowedByOverride(candidate, overrides))
                .filter(candidate -> ScenicPlaceDistance.meters(
                        originLatitude,
                        originLongitude,
                        candidate.latitude(),
                        candidate.longitude()) <= maxDistanceMeters)
                .sorted(Comparator
                        .comparingInt(this::sourcePriority)
                        .thenComparingInt(SpotCandidateRecord::selectionPriority)
                        .thenComparingLong(candidate -> ScenicPlaceDistance.meters(
                                originLatitude,
                                originLongitude,
                                candidate.latitude(),
                                candidate.longitude()))
                        .thenComparing(SpotCandidateRecord::name)
                        .thenComparing(SpotCandidateRecord::id))
                .forEach(candidate -> unique.putIfAbsent(deduplicationKey(candidate), candidate));
        List<SpotCandidateRecord> ordered = List.copyOf(unique.values());
        List<SpotCandidateRecord> sourceDiversified = diversifySourcesWithinThemes(ordered);
        return theme == ScenicTheme.ANYWHERE || theme == ScenicTheme.WATER
                ? diversifyThemes(sourceDiversified, theme, limit)
                : sourceDiversified.stream().limit(limit).toList();
    }

    /** 비활성 검수 행은 같은 이름의 외부 후보를 제외하고, 활성 행은 DB 보정본만 남깁니다. */
    private boolean isAllowedByOverride(
            SpotCandidateRecord candidate,
            Map<String, SpotCandidateOverrideRecord> overrides) {
        SpotCandidateOverrideRecord override = overrides.get(normalizedName(candidate.name()));
        if (override == null) {
            return true;
        }
        return override.enabled() && "VERIFIED_DB".equals(candidate.source());
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
        return "VERIFIED_DB".equals(candidate.source()) ? 1 : 0;
    }

    /** 어디든·강/호수에서는 원천과 무관하게 한 테마가 목록을 독점하지 않도록 번갈아 채웁니다. */
    private List<SpotCandidateRecord> diversifyThemes(
            List<SpotCandidateRecord> ordered,
            ScenicTheme requestedTheme,
            int limit) {
        List<SpotCandidateRecord> result = new ArrayList<>();
        List<ScenicTheme> themeOrder = requestedTheme == ScenicTheme.WATER
                ? List.of(ScenicTheme.RIVER, ScenicTheme.LAKE)
                : ACTUAL_THEME_ORDER;
        Map<ScenicTheme, Deque<SpotCandidateRecord>> queues = new EnumMap<>(ScenicTheme.class);
        themeOrder.forEach(theme -> queues.put(theme, new ArrayDeque<>()));
        ordered.forEach(candidate -> {
            Deque<SpotCandidateRecord> queue = queues.get(candidate.theme());
            if (queue != null) {
                queue.addLast(candidate);
            }
        });

        boolean added;
        do {
            added = false;
            for (ScenicTheme theme : themeOrder) {
                SpotCandidateRecord candidate = queues.get(theme).pollFirst();
                if (candidate != null) {
                    result.add(candidate);
                    added = true;
                    if (result.size() >= limit) {
                        return List.copyOf(result);
                    }
                }
            }
        } while (added);
        return List.copyOf(result);
    }

    /** 같은 테마 안에서도 한 외부 원천이 목록을 독점하지 않도록 원천별로 번갈아 채웁니다. */
    private List<SpotCandidateRecord> diversifySourcesWithinThemes(
            List<SpotCandidateRecord> ordered) {
        Map<ScenicTheme, Map<String, Deque<SpotCandidateRecord>>> byTheme =
                new EnumMap<>(ScenicTheme.class);
        ACTUAL_THEME_ORDER.forEach(theme -> byTheme.put(theme, new LinkedHashMap<>()));
        ordered.forEach(candidate -> {
            Map<String, Deque<SpotCandidateRecord>> sourceQueues = byTheme.get(candidate.theme());
            if (sourceQueues != null) {
                sourceQueues.computeIfAbsent(candidate.source(), ignored -> new ArrayDeque<>())
                        .addLast(candidate);
            }
        });

        List<SpotCandidateRecord> result = new ArrayList<>(ordered.size());
        for (ScenicTheme theme : ACTUAL_THEME_ORDER) {
            var sourceQueues = byTheme.get(theme).values();
            boolean added;
            do {
                added = false;
                for (Deque<SpotCandidateRecord> queue : sourceQueues) {
                    SpotCandidateRecord candidate = queue.pollFirst();
                    if (candidate != null) {
                        result.add(candidate);
                        added = true;
                    }
                }
            } while (added);
        }
        return List.copyOf(result);
    }

    private String deduplicationKey(SpotCandidateRecord candidate) {
        return normalizedName(candidate.name())
                + '|'
                + candidate.latitude().setScale(4, RoundingMode.HALF_UP)
                + '|'
                + candidate.longitude().setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizedName(String name) {
        return name.replaceAll("[^가-힣a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
