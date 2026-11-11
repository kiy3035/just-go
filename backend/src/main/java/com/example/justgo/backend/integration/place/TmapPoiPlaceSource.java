package com.example.justgo.backend.integration.place;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import com.example.justgo.backend.integration.routing.tmap.TmapClientProperties;
import com.example.justgo.backend.spot.ScenicPlaceDistance;
import com.example.justgo.backend.spot.ScenicPlaceSource;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** TMAP 공식 주변 카테고리 중 제품 테마와 직접 대응하는 POI만 조회합니다. */
public class TmapPoiPlaceSource implements ScenicPlaceSource {

    private static final String SOURCE = "TMAP_POI";
    private static final String PATH = "/tmap/pois/search/around";
    private static final String CITY_CATEGORIES = "주요시설물;문화시설;공연장";
    private static final String PARK_CATEGORIES = "공원";
    private static final int MAX_CACHE_ENTRIES = 128;

    private final RestClient restClient;
    private final TmapClientProperties properties;
    private final Clock clock;
    private final Map<String, PoiCache> cache = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<List<SpotCandidateRecord>>> inFlightLoads =
            new ConcurrentHashMap<>();

    public TmapPoiPlaceSource(
            RestClient restClient,
            TmapClientProperties properties,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public Set<ScenicTheme> supportedThemes() {
        return Set.of(ScenicTheme.CITY, ScenicTheme.PARK);
    }

    @Override
    public List<SpotCandidateRecord> findNearby(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        List<SpotCandidateRecord> candidates = new ArrayList<>();
        if (theme.includes(ScenicTheme.CITY)) {
            candidates.addAll(cachedCandidates(
                    ScenicTheme.CITY,
                    CITY_CATEGORIES,
                    originLatitude,
                    originLongitude,
                    maxDistanceMeters));
        }
        if (theme.includes(ScenicTheme.PARK)) {
            candidates.addAll(cachedCandidates(
                    ScenicTheme.PARK,
                    PARK_CATEGORIES,
                    originLatitude,
                    originLongitude,
                    maxDistanceMeters));
        }
        return candidates.stream()
                .filter(candidate -> theme.includes(candidate.theme()))
                .filter(candidate -> ScenicPlaceDistance.meters(
                        originLatitude,
                        originLongitude,
                        candidate.latitude(),
                        candidate.longitude()) <= maxDistanceMeters)
                .sorted((first, second) -> Long.compare(
                        ScenicPlaceDistance.meters(
                                originLatitude, originLongitude,
                                first.latitude(), first.longitude()),
                        ScenicPlaceDistance.meters(
                                originLatitude, originLongitude,
                                second.latitude(), second.longitude())))
                .limit(limit)
                .toList();
    }

    private List<SpotCandidateRecord> cachedCandidates(
            ScenicTheme theme,
            String categories,
            BigDecimal latitude,
            BigDecimal longitude,
            long maxDistanceMeters) {
        int radiusKm = Math.max(1, Math.min(
                properties.getPoiRadiusKm(),
                Math.toIntExact((maxDistanceMeters + 999L) / 1000L)));
        String key = cacheKey(categories, latitude, longitude, radiusKm);
        List<SpotCandidateRecord> cached = cachedValue(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<List<SpotCandidateRecord>> current = new CompletableFuture<>();
        CompletableFuture<List<SpotCandidateRecord>> existing = inFlightLoads.putIfAbsent(
                key, current);
        if (existing != null) {
            return awaitCandidates(existing);
        }

        try {
            List<SpotCandidateRecord> cachedAfterRegistration = cachedValue(key);
            if (cachedAfterRegistration != null) {
                current.complete(cachedAfterRegistration);
                return cachedAfterRegistration;
            }
            List<SpotCandidateRecord> loaded = loadCandidates(
                    theme, categories, latitude, longitude, radiusKm);
            cacheValue(key, loaded);
            current.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error exception) {
            current.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightLoads.remove(key, current);
        }
    }

    private List<SpotCandidateRecord> cachedValue(String key) {
        Instant now = Instant.now(clock);
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            PoiCache cached = cache.get(key);
            return cached == null ? null : cached.candidates();
        }
    }

    private List<SpotCandidateRecord> loadCandidates(
            ScenicTheme theme,
            String categories,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusKm) {
        JsonNode response = fetch(categories, latitude, longitude, radiusKm);
        List<SpotCandidateRecord> loaded = new ArrayList<>();
        for (JsonNode item : responseItems(response)) {
            toCandidate(item, theme, categories).ifPresent(loaded::add);
        }
        return List.copyOf(loaded);
    }

    private void cacheValue(String key, List<SpotCandidateRecord> candidates) {
        synchronized (cache) {
            while (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, new PoiCache(
                    candidates,
                    Instant.now(clock).plus(properties.getPoiCacheTtl())));
        }
    }

    /** 같은 좌표·카테고리의 진행 중 POI 조회를 공유해 외부 호출량을 줄입니다. */
    private List<SpotCandidateRecord> awaitCandidates(
            CompletableFuture<List<SpotCandidateRecord>> inFlight) {
        try {
            return inFlight.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private JsonNode fetch(
            String categories,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusKm) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH)
                            .queryParam("version", 1)
                            .queryParam("centerLon", longitude.toPlainString())
                            .queryParam("centerLat", latitude.toPlainString())
                            .queryParam("categories", categories)
                            .queryParam("page", 1)
                            .queryParam("count", properties.getPoiPageSize())
                            .queryParam("radius", radiusKm)
                            .queryParam("reqCoordType", "WGS84GEO")
                            .queryParam("resCoordType", "WGS84GEO")
                            .queryParam("multiPoint", "Y")
                            .queryParam("sort", "distance")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
                        throw new PublicDataClientException("TMAP POI HTTP 호출이 실패했습니다.");
                    })
                    .body(JsonNode.class);
            if (response == null) {
                throw new PublicDataClientException("TMAP POI 응답이 비어 있습니다.");
            }
            return response;
        } catch (PublicDataClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PublicDataClientException("TMAP POI 네트워크 호출이 실패했습니다.", exception);
        }
    }

    private List<JsonNode> responseItems(JsonNode response) {
        JsonNode items = response.path("searchPoiInfo").path("pois").path("poi");
        if (items.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        }
        return items.isObject() ? List.of(items) : List.of();
    }

    private java.util.Optional<SpotCandidateRecord> toCandidate(
            JsonNode item,
            ScenicTheme theme,
            String requestedCategories) {
        String id = text(item, "id", "pkey");
        String name = text(item, "name");
        BigDecimal latitude = firstDecimal(item, "frontLat", "noorLat");
        BigDecimal longitude = firstDecimal(item, "frontLon", "noorLon");
        if (id == null || name == null || name.isBlank()
                || !validCoordinate(latitude, longitude)) {
            return java.util.Optional.empty();
        }
        String sourceCategory = text(
                item,
                "detailBizName",
                "lowerBizName",
                "middleBizName",
                "upperBizName");
        return java.util.Optional.of(new SpotCandidateRecord(
                ExternalCandidateId.from(SOURCE, id),
                name.strip(),
                theme,
                latitude,
                longitude,
                SOURCE,
                sourceCategory == null ? requestedCategories : sourceCategory.strip()));
    }

    private String cacheKey(
            String categories,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusKm) {
        String rawKey = categories
                + '|'
                + latitude.stripTrailingZeros().toPlainString()
                + '|'
                + longitude.stripTrailingZeros().toPlainString()
                + '|'
                + radiusKm;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("필수 SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value == null) {
                continue;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ignored) {
                // 다음 공식 좌표 필드를 사용합니다.
            }
        }
        return null;
    }

    private boolean validCoordinate(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private record PoiCache(List<SpotCandidateRecord> candidates, Instant expiresAt) {
    }
}
