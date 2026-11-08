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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.justgo.backend.spot.ScenicPlaceDistance;
import com.example.justgo.backend.spot.ScenicPlaceSource;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** TourAPI 위치기반 관광정보를 넓은 자연·도시 후보로 정규화합니다. */
public class TourApiScenicPlaceSource implements ScenicPlaceSource {

    private static final String SOURCE = "TOUR_API";
    private static final String PATH = "/locationBasedList2";
    private static final Set<String> SCENIC_CONTENT_TYPES = Set.of("12", "14", "25", "28");
    private static final int MAX_CACHE_ENTRIES = 64;
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private static final int CENTERS_PER_RING = 6;

    private final RestClient restClient;
    private final PublicDataProperties properties;
    private final Clock clock;
    private final Map<String, TourCache> cache = new LinkedHashMap<>();

    public TourApiScenicPlaceSource(
            RestClient restClient,
            PublicDataProperties properties) {
        this(restClient, properties, Clock.systemUTC());
    }

    TourApiScenicPlaceSource(
            RestClient restClient,
            PublicDataProperties properties,
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
        return Set.of(
                ScenicTheme.CITY,
                ScenicTheme.MOUNTAIN,
                ScenicTheme.RIVER,
                ScenicTheme.LAKE,
                ScenicTheme.SEA,
                ScenicTheme.PARK);
    }

    @Override
    public List<SpotCandidateRecord> findNearby(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        int radius = Math.toIntExact(Math.min(
                maxDistanceMeters,
                properties.getTourRadiusMeters()));
        return cachedCandidates(
                        originLatitude,
                        originLongitude,
                        radius,
                        maxDistanceMeters)
                .stream()
                .filter(candidate -> theme.includes(candidate.theme()))
                .filter(candidate -> ScenicPlaceDistance.meters(
                        originLatitude,
                        originLongitude,
                        candidate.latitude(),
                        candidate.longitude()) <= maxDistanceMeters)
                .sorted((first, second) -> {
                    int priority = Integer.compare(
                            first.selectionPriority(), second.selectionPriority());
                    if (priority != 0) {
                        return priority;
                    }
                    return Long.compare(
                            ScenicPlaceDistance.meters(
                                    originLatitude, originLongitude,
                                    first.latitude(), first.longitude()),
                            ScenicPlaceDistance.meters(
                                    originLatitude, originLongitude,
                                    second.latitude(), second.longitude()));
                })
                .limit(limit)
                .toList();
    }

    /** 같은 위치·범위의 다중 중심 조회는 원시 좌표 대신 해시 키로 10분간 재사용합니다. */
    private List<SpotCandidateRecord> cachedCandidates(
            BigDecimal latitude,
            BigDecimal longitude,
            int radius,
            long maxDistanceMeters) {
        String key = cacheKey(latitude, longitude, radius, maxDistanceMeters);
        Instant now = Instant.now(clock);
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            TourCache cached = cache.get(key);
            if (cached != null) {
                return cached.candidates();
            }
        }

        Map<Long, SpotCandidateRecord> loaded = new LinkedHashMap<>();
        for (SearchCenter center : searchCenters(latitude, longitude, radius, maxDistanceMeters)) {
            JsonNode response = fetch(center.latitude(), center.longitude(), radius);
            for (JsonNode item : responseItems(response)) {
                toCandidate(item).ifPresent(candidate -> loaded.putIfAbsent(
                        candidate.id(), candidate));
            }
        }
        List<SpotCandidateRecord> immutable = List.copyOf(loaded.values());
        synchronized (cache) {
            while (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, new TourCache(
                    immutable,
                    Instant.now(clock).plus(properties.getTourCacheTtl())));
        }
        return immutable;
    }

    private String cacheKey(
            BigDecimal latitude,
            BigDecimal longitude,
            int radius,
            long maxDistanceMeters) {
        String rawKey = latitude.stripTrailingZeros().toPlainString()
                + '|'
                + longitude.stripTrailingZeros().toPlainString()
                + '|'
                + radius
                + '|'
                + maxDistanceMeters
                + '|'
                + properties.getTourSearchMaxCenters()
                + '|'
                + properties.getTourSearchRingStepMeters();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("필수 SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    /** 20km 단일 반경을 넘어설 때 원점 주위 육각 링을 제한된 개수만 조회합니다. */
    private List<SearchCenter> searchCenters(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            int radius,
            long maxDistanceMeters) {
        List<SearchCenter> centers = new ArrayList<>();
        centers.add(new SearchCenter(originLatitude, originLongitude));
        if (maxDistanceMeters <= radius || properties.getTourSearchMaxCenters() == 1) {
            return List.copyOf(centers);
        }

        long ringDistance = properties.getTourSearchRingStepMeters();
        while (centers.size() < properties.getTourSearchMaxCenters()
                && ringDistance - radius <= maxDistanceMeters) {
            for (int index = 0;
                    index < CENTERS_PER_RING
                            && centers.size() < properties.getTourSearchMaxCenters();
                    index++) {
                centers.add(destination(
                        originLatitude,
                        originLongitude,
                        ringDistance,
                        index * (360.0 / CENTERS_PER_RING)));
            }
            ringDistance += properties.getTourSearchRingStepMeters();
        }
        return List.copyOf(centers);
    }

    /** 구면 지구 근사로 원점에서 거리·방위각만큼 이동한 WGS84 검색 중심을 계산합니다. */
    private SearchCenter destination(
            BigDecimal latitude,
            BigDecimal longitude,
            long distanceMeters,
            double bearingDegrees) {
        double latitudeRadians = Math.toRadians(latitude.doubleValue());
        double longitudeRadians = Math.toRadians(longitude.doubleValue());
        double bearingRadians = Math.toRadians(bearingDegrees);
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;
        double destinationLatitude = Math.asin(
                Math.sin(latitudeRadians) * Math.cos(angularDistance)
                        + Math.cos(latitudeRadians) * Math.sin(angularDistance)
                        * Math.cos(bearingRadians));
        double destinationLongitude = longitudeRadians + Math.atan2(
                Math.sin(bearingRadians) * Math.sin(angularDistance)
                        * Math.cos(latitudeRadians),
                Math.cos(angularDistance)
                        - Math.sin(latitudeRadians) * Math.sin(destinationLatitude));
        destinationLongitude = (destinationLongitude + 3 * Math.PI) % (2 * Math.PI) - Math.PI;
        return new SearchCenter(
                BigDecimal.valueOf(Math.toDegrees(destinationLatitude)),
                BigDecimal.valueOf(Math.toDegrees(destinationLongitude)));
    }

    private JsonNode fetch(BigDecimal latitude, BigDecimal longitude, int radius) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH)
                            .queryParam("serviceKey", properties.getTourApiKey())
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "just-go")
                            .queryParam("_type", "json")
                            .queryParam("arrange", "E")
                            .queryParam("mapX", longitude.toPlainString())
                            .queryParam("mapY", latitude.toPlainString())
                            .queryParam("radius", radius)
                            .queryParam("numOfRows", properties.getTourPageSize())
                            .queryParam("pageNo", 1)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
                        throw new PublicDataClientException("TourAPI HTTP 호출이 실패했습니다.");
                    })
                    .body(JsonNode.class);
            if (response == null) {
                throw new PublicDataClientException("TourAPI 응답이 비어 있습니다.");
            }
            String resultCode = text(
                    response.path("response").path("header"),
                    "resultCode");
            if (resultCode != null && !Set.of("0000", "00", "0").contains(resultCode)) {
                throw new PublicDataClientException("TourAPI 제공자가 오류를 반환했습니다.");
            }
            return response;
        } catch (PublicDataClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PublicDataClientException("TourAPI 네트워크 호출이 실패했습니다.", exception);
        }
    }

    private List<JsonNode> responseItems(JsonNode response) {
        JsonNode items = response.path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        }
        return items.isObject() ? List.of(items) : List.of();
    }

    private java.util.Optional<SpotCandidateRecord> toCandidate(JsonNode item) {
        String contentType = text(item, "contenttypeid", "contentTypeId");
        if (contentType != null && !SCENIC_CONTENT_TYPES.contains(contentType)) {
            return java.util.Optional.empty();
        }
        String title = text(item, "title");
        String contentId = text(item, "contentid", "contentId");
        BigDecimal latitude = decimal(item, "mapy", "mapY");
        BigDecimal longitude = decimal(item, "mapx", "mapX");
        if (title == null || title.isBlank() || contentId == null
                || !validCoordinate(latitude, longitude)) {
            return java.util.Optional.empty();
        }
        return classify(item).map(theme -> new SpotCandidateRecord(
                ExternalCandidateId.from(SOURCE, contentId),
                title.strip(),
                theme,
                latitude,
                longitude,
                SOURCE,
                selectionPriority(item, theme),
                sourceCategory(item)));
    }

    /** 공식 신분류 코드가 제품 테마와 명확히 대응하는 항목만 사용합니다. */
    private java.util.Optional<ScenicTheme> classify(JsonNode item) {
        String level2 = valueOrEmpty(text(item, "lclsSystm2")).toUpperCase(Locale.ROOT);
        String level3 = valueOrEmpty(text(item, "lclsSystm3")).toUpperCase(Locale.ROOT);

        if (Set.of("NA020500", "NA020600", "NA020700", "NA020800", "NA020900", "VE010800")
                .contains(level3)) {
            return java.util.Optional.of(ScenicTheme.SEA);
        }
        if (Set.of("NA020200", "NA020300", "NA020400", "NA030400", "VE010700")
                .contains(level3)) {
            return java.util.Optional.of(ScenicTheme.LAKE);
        }
        if (Set.of("NA010300", "NA020100", "LS020500").contains(level3)) {
            return java.util.Optional.of(ScenicTheme.RIVER);
        }
        if (level3.equals("LS020600")) {
            return java.util.Optional.of(ScenicTheme.SEA);
        }
        if (level2.equals("NA01") || level2.equals("NA03") || level2.equals("NA04")) {
            if (level3.equals("NA040700")) {
                return java.util.Optional.of(ScenicTheme.PARK);
            }
            return java.util.Optional.of(ScenicTheme.MOUNTAIN);
        }
        if (level2.equals("VE02") || level2.equals("VE03")) {
            return java.util.Optional.of(ScenicTheme.PARK);
        }
        if (Set.of(
                "VE01", "VE04", "VE05", "VE06", "VE07",
                "VE09", "VE10", "VE11", "VE12",
                "HS01", "HS02", "HS03", "HS04").contains(level2)) {
            return java.util.Optional.of(ScenicTheme.CITY);
        }
        return java.util.Optional.empty();
    }

    private String sourceCategory(JsonNode item) {
        String level2 = valueOrEmpty(text(item, "lclsSystm2")).toUpperCase(Locale.ROOT);
        String level3 = valueOrEmpty(text(item, "lclsSystm3")).toUpperCase(Locale.ROOT);
        return level3.isBlank() ? level2 : level2 + '/' + level3;
    }

    /** CITY 안에서는 공식 랜드마크·전시·역사 분류를 일반 시설보다 먼저 보여 줍니다. */
    private int selectionPriority(JsonNode item, ScenicTheme theme) {
        if (theme != ScenicTheme.CITY) {
            return 0;
        }
        String level2 = valueOrEmpty(text(item, "lclsSystm2")).toUpperCase(Locale.ROOT);
        String contentType = valueOrEmpty(text(item, "contenttypeid", "contentTypeId"));
        if (level2.equals("VE01")) {
            return 0;
        }
        if (Set.of("VE07", "HS01", "HS02", "HS04").contains(level2)) {
            return 1;
        }
        if (Set.of("VE04", "VE05", "VE06").contains(level2)) {
            return 2;
        }
        if (level2.equals("HS03") || contentType.equals("25")) {
            return 3;
        }
        if (Set.of("VE09", "VE10", "VE11", "VE12").contains(level2)
                || contentType.equals("28")) {
            return 5;
        }
        return 4;
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

    private BigDecimal decimal(JsonNode node, String... fieldNames) {
        String value = text(node, fieldNames);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean validCoordinate(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TourCache(
            List<SpotCandidateRecord> candidates,
            Instant expiresAt) {
    }

    private record SearchCenter(BigDecimal latitude, BigDecimal longitude) {
    }
}
