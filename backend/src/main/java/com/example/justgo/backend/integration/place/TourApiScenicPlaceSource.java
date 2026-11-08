package com.example.justgo.backend.integration.place;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    private final RestClient restClient;
    private final PublicDataProperties properties;

    public TourApiScenicPlaceSource(
            RestClient restClient,
            PublicDataProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
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
        JsonNode response = fetch(originLatitude, originLongitude, radius);
        List<SpotCandidateRecord> candidates = new ArrayList<>();
        for (JsonNode item : responseItems(response)) {
            toCandidate(item).filter(candidate -> theme.includes(candidate.theme()))
                    .ifPresent(candidates::add);
        }
        return candidates.stream().limit(limit).toList();
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
        String taxonomy = String.join(" ",
                valueOrEmpty(title),
                valueOrEmpty(text(item, "cat1")),
                valueOrEmpty(text(item, "cat2")),
                valueOrEmpty(text(item, "cat3")),
                valueOrEmpty(text(item, "lclsSystm1")),
                valueOrEmpty(text(item, "lclsSystm2")),
                valueOrEmpty(text(item, "lclsSystm3")))
                .toLowerCase(Locale.ROOT);
        ScenicTheme theme = classify(taxonomy);
        return java.util.Optional.of(new SpotCandidateRecord(
                ExternalCandidateId.from(SOURCE, contentId),
                title.strip(),
                theme,
                latitude,
                longitude,
                SOURCE));
    }

    private ScenicTheme classify(String text) {
        if (containsAny(text, "해수욕", "해변", "해안", "바다", "항구", "포구", "등대", "갯벌", "섬")) {
            return ScenicTheme.SEA;
        }
        if (containsAny(text, "호수", "저수지", "댐", "습지", "연못")) {
            return ScenicTheme.LAKE;
        }
        if (containsAny(text, "한강", "낙동강", "금강", "영산강", "섬진강", "강변", "수변", "하천", "나루", "폭포")) {
            return ScenicTheme.RIVER;
        }
        if (containsAny(text, "산림", "휴양림", "수목원", "계곡", "고개", "오름", "숲")
                || text.endsWith("산") || text.contains("산 ") || text.contains("산국립")) {
            return ScenicTheme.MOUNTAIN;
        }
        if (text.contains("공원")) {
            return ScenicTheme.PARK;
        }
        return ScenicTheme.CITY;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
}
