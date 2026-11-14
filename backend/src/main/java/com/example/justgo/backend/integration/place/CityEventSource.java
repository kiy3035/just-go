package com.example.justgo.backend.integration.place;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 전국공연행사 표준데이터에서 위치와 검색 날짜의 운영시간을 확인할 수 있는 행사만 읽는다. */
@Component
@Profile("tmap & !mock")
public class CityEventSource {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "NATIONAL_PERFORMANCE_EVENTS";
    private static final int PAGE_SIZE = 1_000;
    private static final int MAX_PAGES = 20;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient client;
    private final String key;
    private Snapshot cached = new Snapshot(Instant.EPOCH, List.of());

    public CityEventSource(RestClient.Builder builder, @Value("${CITY_EVENT_API_KEY:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.client = builder.baseUrl("https://api.data.go.kr/openapi").requestFactory(factory).build();
        this.key = key;
    }

    public synchronized List<Event> events(ZonedDateTime departure) {
        if (key.isBlank()) return List.of();
        Instant now = departure.toInstant();
        if (!now.isBefore(cached.expires())) {
            try {
                cached = new Snapshot(now.plus(CACHE_TTL), fetchEvents());
            } catch (RestClientException | PublicDataClientException exception) {
                cached = new Snapshot(now.plusSeconds(60), List.of());
            }
        }
        LocalDate serviceDate = departure.withZoneSameInstant(SEOUL).toLocalDate();
        return cached.schedules().stream()
                .map(schedule -> schedule.occurrenceOn(serviceDate))
                .flatMap(Optional::stream)
                .filter(event -> departure.isBefore(event.closes()))
                .toList();
    }

    private List<EventSchedule> fetchEvents() {
        List<EventSchedule> schedules = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            int pageNumber = page;
            JsonNode json = client.get().uri(uri -> uri.path("/tn_pubr_public_pblprfr_event_info_api")
                    .queryParam("serviceKey", "{key}").queryParam("type", "json")
                    .queryParam("pageNo", pageNumber).queryParam("numOfRows", PAGE_SIZE).build(key))
                    .retrieve().body(JsonNode.class);
            if (json == null) throw new PublicDataClientException("행사 응답이 비어 있습니다.");
            JsonNode envelope = json.has("response") ? json.path("response") : json;
            String code = envelope.path("header").path("resultCode").asText();
            if (!List.of("00", "0", "NORMAL_SERVICE").contains(code)) {
                throw new PublicDataClientException("행사 데이터 제공자가 오류를 반환했습니다.");
            }
            JsonNode body = envelope.path("body");
            JsonNode items = body.path("items");
            if (items.has("item")) items = items.path("item");
            if (items.isArray()) {
                items.forEach(item -> parse(item).ifPresent(schedules::add));
            } else if (items.isObject()) {
                parse(items).ifPresent(schedules::add);
            }
            if (page * PAGE_SIZE >= body.path("totalCount").asInt(0)) break;
        }
        return List.copyOf(schedules);
    }

    static Optional<EventSchedule> parse(JsonNode item) {
        String name = item.path("eventNm").asText().strip();
        String venue = item.path("opar").asText().strip();
        if (name.isBlank() || venue.isBlank()) return Optional.empty();
        try {
            LocalDate firstDay = LocalDate.parse(item.path("eventStartDate").asText().strip());
            LocalDate lastDay = LocalDate.parse(item.path("eventEndDate").asText().strip());
            LocalTime opensAt = LocalTime.parse(item.path("eventStartTime").asText().strip());
            LocalTime closesAt = LocalTime.parse(item.path("eventEndTime").asText().strip());
            if (lastDay.isBefore(firstDay) || !closesAt.isAfter(opensAt)) return Optional.empty();
            BigDecimal latitude = new BigDecimal(item.path("latitude").asText(item.path("lat").asText()).strip());
            BigDecimal longitude = new BigDecimal(item.path("longitude").asText(item.path("lot").asText()).strip());
            if (latitude.signum() <= 0 || longitude.signum() <= 0
                    || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                    || longitude.compareTo(BigDecimal.valueOf(180)) > 0) return Optional.empty();
            long id = ExternalCandidateId.from(SOURCE, name + "|" + venue + "|" + firstDay + "|"
                    + lastDay + "|" + opensAt + "|" + closesAt);
            return Optional.of(new EventSchedule(new SpotCandidateRecord(id, venue, ScenicTheme.CITY,
                    latitude, longitude, SOURCE), name, firstDay, lastDay, opensAt, closesAt));
        } catch (DateTimeParseException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** 여러 날 행사는 시작일부터 종료일까지 매일 같은 운영시간을 적용한다. */
    record EventSchedule(SpotCandidateRecord venue, String name, LocalDate firstDay,
                         LocalDate lastDay, LocalTime opensAt, LocalTime closesAt) {
        Optional<Event> occurrenceOn(LocalDate date) {
            if (date.isBefore(firstDay) || date.isAfter(lastDay)) return Optional.empty();
            return Optional.of(new Event(venue, name,
                    date.atTime(opensAt).atZone(SEOUL), date.atTime(closesAt).atZone(SEOUL)));
        }
    }

    public record Event(SpotCandidateRecord venue, String name, ZonedDateTime opens, ZonedDateTime closes) {
        public boolean canVisitAt(ZonedDateTime arrival) {
            return !arrival.isBefore(opens) && arrival.isBefore(closes);
        }
    }

    private record Snapshot(Instant expires, List<EventSchedule> schedules) {}
}
