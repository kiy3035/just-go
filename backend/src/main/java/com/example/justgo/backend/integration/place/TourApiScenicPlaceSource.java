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
        ScenicTheme theme = classify(item, title);
        return java.util.Optional.of(new SpotCandidateRecord(
                ExternalCandidateId.from(SOURCE, contentId),
                title.strip(),
                theme,
                latitude,
                longitude,
                SOURCE,
                selectionPriority(item, theme)));
    }

    /** 공식 신분류 코드를 우선하고, 코드가 없는 레거시 항목만 장소명 키워드로 보완합니다. */
    private ScenicTheme classify(JsonNode item, String title) {
        String level2 = valueOrEmpty(text(item, "lclsSystm2")).toUpperCase(Locale.ROOT);
        String level3 = valueOrEmpty(text(item, "lclsSystm3")).toUpperCase(Locale.ROOT);

        if (Set.of("NA020500", "NA020600", "NA020700", "NA020800", "NA020900", "VE010800")
                .contains(level3)) {
            return ScenicTheme.SEA;
        }
        if (Set.of("NA020200", "NA020300", "NA020400", "NA030400", "VE010700")
                .contains(level3)) {
            return ScenicTheme.LAKE;
        }
        if (Set.of("NA010300", "NA020100", "LS020500").contains(level3)) {
            return ScenicTheme.RIVER;
        }
        if (level2.equals("LS02")) {
            return level3.equals("LS020600") || hasSeaKeyword(title)
                    ? ScenicTheme.SEA
                    : ScenicTheme.RIVER;
        }
        if (level2.equals("NA01") || level2.equals("NA03") || level2.equals("NA04")) {
            if (level3.equals("NA040700")) {
                return ScenicTheme.PARK;
            }
            return ScenicTheme.MOUNTAIN;
        }
        if (level2.equals("VE02") || level2.equals("VE03")) {
            return ScenicTheme.PARK;
        }
        if (level2.equals("NA05")) {
            return title.contains("공원") || title.contains("정원")
                    ? ScenicTheme.PARK
                    : ScenicTheme.MOUNTAIN;
        }
        if (!level2.isBlank() && !level2.equals("NA02")) {
            return ScenicTheme.CITY;
        }

        String normalizedTitle = title.toLowerCase(Locale.ROOT).strip();
        if (hasSeaKeyword(normalizedTitle)) {
            return ScenicTheme.SEA;
        }
        if (containsAny(normalizedTitle, "호수", "저수지", "댐", "습지", "연못")) {
            return ScenicTheme.LAKE;
        }
        if (containsAny(normalizedTitle, "한강", "낙동강", "금강", "영산강", "섬진강", "강변", "수변", "하천", "나루", "폭포")) {
            return ScenicTheme.RIVER;
        }
        if (containsAny(normalizedTitle, "산림", "휴양림", "수목원", "계곡", "고개", "오름", "숲")
                || normalizedTitle.matches(".*(?:산|봉)$")) {
            return ScenicTheme.MOUNTAIN;
        }
        if (normalizedTitle.contains("공원")) {
            return ScenicTheme.PARK;
        }
        return ScenicTheme.CITY;
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

    private boolean hasSeaKeyword(String text) {
        return containsAny(text, "해수욕", "해변", "해안", "바다", "항구", "포구", "등대", "갯벌", "섬");
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
