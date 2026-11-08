package com.example.justgo.backend.integration.place;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** 전국도시공원정보표준데이터를 24시간 미만 메모리 캐시로 조회합니다. */
public class ParkOpenDataSource implements ScenicPlaceSource {

    private static final String SOURCE = "PUBLIC_PARK";
    private static final String PATH = "/tn_pubr_public_cty_park_info_api";

    private final RestClient restClient;
    private final PublicDataProperties properties;
    private final Clock clock;
    private volatile ParkCache cache = new ParkCache(List.of(), Instant.EPOCH);

    public ParkOpenDataSource(
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
        return Set.of(ScenicTheme.PARK);
    }

    @Override
    public List<SpotCandidateRecord> findNearby(
            ScenicTheme theme,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            long maxDistanceMeters,
            int limit) {
        if (!theme.includes(ScenicTheme.PARK)) {
            return List.of();
        }
        return parks().stream()
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

    private List<SpotCandidateRecord> parks() {
        ParkCache current = cache;
        if (current.expiresAt().isAfter(Instant.now(clock))) {
            return current.candidates();
        }
        synchronized (this) {
            current = cache;
            if (current.expiresAt().isAfter(Instant.now(clock))) {
                return current.candidates();
            }
            List<SpotCandidateRecord> loaded = loadAllPages();
            cache = new ParkCache(
                    loaded,
                    Instant.now(clock).plus(properties.getParkCacheTtl()));
            return loaded;
        }
    }

    private List<SpotCandidateRecord> loadAllPages() {
        List<SpotCandidateRecord> candidates = new ArrayList<>();
        for (int page = 1; page <= properties.getParkMaxPages(); page++) {
            JsonNode response = fetchPage(page);
            List<JsonNode> items = responseItems(response);
            for (JsonNode item : items) {
                toCandidate(item).ifPresent(candidates::add);
            }
            if (items.size() < properties.getParkPageSize()) {
                break;
            }
        }
        if (candidates.isEmpty()) {
            throw new PublicDataClientException("공원 데이터에 유효한 후보가 없습니다.");
        }
        return List.copyOf(candidates);
    }

    private JsonNode fetchPage(int page) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH)
                            .queryParam("serviceKey", properties.getParkApiKey())
                            .queryParam("type", "json")
                            .queryParam("pageNo", page)
                            .queryParam("numOfRows", properties.getParkPageSize())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
                        throw new PublicDataClientException("공원 데이터 HTTP 호출이 실패했습니다.");
                    })
                    .body(JsonNode.class);
            if (response == null) {
                throw new PublicDataClientException("공원 데이터 응답이 비어 있습니다.");
            }
            validateHeader(response);
            return response;
        } catch (PublicDataClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PublicDataClientException("공원 데이터 네트워크 호출이 실패했습니다.", exception);
        }
    }

    private void validateHeader(JsonNode response) {
        JsonNode header = response.path("response").path("header");
        if (header.isMissingNode()) {
            header = response.path("header");
        }
        String code = text(header, "resultCode", "RESULT_CODE");
        if (code != null && !Set.of("00", "0", "NORMAL_SERVICE").contains(code)) {
            throw new PublicDataClientException("공원 데이터 제공자가 오류를 반환했습니다.");
        }
    }

    private List<JsonNode> responseItems(JsonNode response) {
        JsonNode body = response.path("response").path("body");
        if (body.isMissingNode()) {
            body = response.path("body");
        }
        JsonNode items = body.path("items");
        if (items.isObject() && items.has("item")) {
            items = items.path("item");
        }
        if (items.isMissingNode() || items.isNull()) {
            items = response.path("data");
        }
        if (items.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        }
        if (items.isObject()) {
            return List.of(items);
        }
        return List.of();
    }

    private java.util.Optional<SpotCandidateRecord> toCandidate(JsonNode item) {
        String name = text(item, "PARK_NM", "parkNm", "parkName");
        BigDecimal latitude = decimal(item, "LATITUDE", "latitude", "lat");
        BigDecimal longitude = decimal(item, "LONGITUDE", "longitude", "lot", "lng");
        if (name == null || name.isBlank() || !validCoordinate(latitude, longitude)) {
            return java.util.Optional.empty();
        }
        String externalId = text(item, "MANAGE_NO", "manageNo", "managementNo");
        if (externalId == null || externalId.isBlank()) {
            externalId = name + '|' + latitude + '|' + longitude;
        }
        String parkType = text(item, "PARK_SE", "parkSe", "parkType");
        return java.util.Optional.of(new SpotCandidateRecord(
                ExternalCandidateId.from(SOURCE, externalId),
                name.strip(),
                ScenicTheme.PARK,
                latitude,
                longitude,
                SOURCE,
                parkType == null ? "도시공원" : parkType.strip()));
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

    private record ParkCache(List<SpotCandidateRecord> candidates, Instant expiresAt) {
    }
}
