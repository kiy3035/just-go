package com.example.justgo.backend.integration.place;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** 홈 행사 배너용 공개 장소만 조회한다. 드라이브 추천 및 TMAP 호출과 독립적이다. */
@Service
public class TodayEventSource {
    private static final int SEOUL_PAGE_SIZE = 1000;
    private static final int TOUR_PAGE_SIZE = 100;
    private static final Pattern CLOCK_RANGE = Pattern.compile(
            "(\\d{1,2}:\\d{2})\\s*[-~–]\\s*(\\d{1,2}:\\d{2})");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

    private final RestClient seoul;
    private final RestClient tour;
    private final String seoulKey;
    private final String tourKey;
    private Snapshot cachedSeoul;
    private Snapshot cachedGyeonggi;

    public TodayEventSource(RestClient.Builder builder,
            @Value("${SEOUL_EVENT_API_KEY:}") String seoulKey,
            @Value("${TOUR_API_KEY:}") String tourKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.seoul = builder.clone().baseUrl("http://openapi.seoul.go.kr:8088")
                .requestFactory(factory).build();
        this.tour = builder.clone().baseUrl("https://apis.data.go.kr/B551011/KorService2")
                .requestFactory(factory).build();
        this.seoulKey = seoulKey;
        this.tourKey = tourKey;
    }

    public synchronized Result today() {
        ZonedDateTime now = ZonedDateTime.now(CityEventSource.SEOUL);
        LocalDate date = now.toLocalDate();
        // 같은 날짜의 조회를 서버에서 공유하고, 행사 운영 종료는 응답 시마다 확인한다.
        cachedSeoul = refresh(cachedSeoul, now, seoulKey, () -> fetchSeoul(date));
        cachedGyeonggi = refresh(cachedGyeonggi, now, tourKey, () -> fetchGyeonggi(date));
        if (cachedSeoul.events().isEmpty() && cachedGyeonggi.events().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "행사 정보를 불러오지 못했어요.");
        }
        List<BannerEvent> seoulEvents = available(cachedSeoul.events(), now.toLocalTime());
        List<BannerEvent> gyeonggiEvents = available(cachedGyeonggi.events(), now.toLocalTime());
        List<BannerEvent> events = new ArrayList<>();
        // 각 지역의 후보를 번갈아 담아 한 지역만 배너를 차지하지 않도록 한다.
        for (int index = 0; events.size() < 5
                && index < Math.max(seoulEvents.size(), gyeonggiEvents.size()); index++) {
            if (index < seoulEvents.size()) events.add(seoulEvents.get(index));
            if (events.size() < 5 && index < gyeonggiEvents.size()) events.add(gyeonggiEvents.get(index));
        }
        return new Result(List.copyOf(events), cachedSeoul.events().isEmpty() || cachedGyeonggi.events().isEmpty());
    }

    private static Snapshot refresh(Snapshot snapshot, ZonedDateTime now, String key,
                                    Supplier<List<Event>> fetch) {
        LocalDate date = now.toLocalDate();
        if (snapshot != null && date.equals(snapshot.date()) && now.isBefore(snapshot.expires())) {
            return snapshot;
        }
        Optional<List<Event>> events = load(key, fetch);
        return new Snapshot(date, now.plusMinutes(events.isPresent() ? 60 : 5), events);
    }

    private static List<BannerEvent> available(Optional<List<Event>> events, LocalTime time) {
        return events.orElse(List.of()).stream()
                .filter(event -> event.closes() == null || time.isBefore(event.closes()))
                .map(Event::banner).distinct().toList();
    }

    private static Optional<List<Event>> load(String key, Supplier<List<Event>> fetch) {
        if (key.isBlank()) return Optional.empty();
        try {
            return Optional.of(fetch.get());
        } catch (RestClientException | PublicDataClientException exception) {
            return Optional.empty();
        }
    }

    private List<Event> fetchSeoul(LocalDate date) {
        List<Event> events = new ArrayList<>();
        for (int first = 1; first <= 30_000; first += SEOUL_PAGE_SIZE) {
            int start = first;
            int end = start + SEOUL_PAGE_SIZE - 1;
            JsonNode response = seoul.get().uri(uri -> uri
                    .pathSegment("{key}", "json", "culturalEventInfo", "{start}", "{end}")
                    .path("/").build(seoulKey, start, end)).retrieve().body(JsonNode.class);
            requireBody(response);
            JsonNode data = response.path("culturalEventInfo");
            String code = response.path("RESULT").path("CODE").asText(data.path("RESULT").path("CODE").asText());
            if ("INFO-200".equals(code)) return events;
            if (!"INFO-000".equals(code) || !data.path("row").isArray()) throw invalidResponse();
            for (JsonNode row : data.path("row")) {
                parse(row, date, true).ifPresent(events::add);
            }
            if (end >= data.path("list_total_count").asInt(Integer.MAX_VALUE)) return events;
        }
        throw invalidResponse();
    }

    private List<Event> fetchGyeonggi(LocalDate date) {
        List<Event> events = new ArrayList<>();
        for (int page = 1; page <= 30; page++) {
            int pageNumber = page;
            JsonNode response = tour.get().uri(uri -> uri.path("/searchFestival2")
                    .queryParam("serviceKey", "{key}").queryParam("MobileOS", "AND")
                    .queryParam("MobileApp", "justGo").queryParam("_type", "json")
                    .queryParam("lDongRegnCd", "41").queryParam("arrange", "C")
                    .queryParam("eventStartDate", date.minusYears(1).format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("pageNo", pageNumber).queryParam("numOfRows", TOUR_PAGE_SIZE)
                    .build(tourKey)).retrieve().body(JsonNode.class);
            requireBody(response);
            JsonNode data = response.path("response");
            if (!"00".equals(data.path("header").path("resultCode").asText())) throw invalidResponse();
            JsonNode body = data.path("body");
            int total = body.path("totalCount").asInt(-1);
            if (total == 0) return events;
            JsonNode items = body.path("items").path("item");
            if (total < 0 || !items.isArray()) throw invalidResponse();
            for (JsonNode row : items) {
                parse(row, date, false).ifPresent(events::add);
            }
            if (page * TOUR_PAGE_SIZE >= total) return events;
        }
        throw invalidResponse();
    }

    static Optional<Event> parse(JsonNode row, LocalDate date, boolean seoul) {
        String title = row.path(seoul ? "TITLE" : "title").asText().strip();
        String venue = row.path(seoul ? "PLACE" : "addr1").asText().strip();
        String area = row.path("GUNAME").asText().strip();
        String region = seoul && !area.isEmpty() ? "서울 " + area : seoul ? "서울" : "경기";
        try {
            LocalDate first = parseDate(row.path(seoul ? "STRTDATE" : "eventstartdate").asText(), seoul);
            LocalDate last = parseDate(row.path(seoul ? "END_DATE" : "eventenddate").asText(), seoul);
            double latitude = Double.parseDouble(row.path(seoul ? "LAT" : "mapy").asText());
            double longitude = Double.parseDouble(row.path(seoul ? "LOT" : "mapx").asText());
            // 수도권 범위를 벗어나거나 위경도가 뒤집힌 장소는 내비에 보낼 후보에서 제외한다.
            if (title.isEmpty() || venue.isEmpty() || date.isBefore(first) || date.isAfter(last)
                    || !(latitude >= 36.8 && latitude <= 38.4 && longitude >= 126 && longitude <= 128)) {
                return Optional.empty();
            }
            String hours = row.path("PRO_TIME").asText().strip();
            var match = CLOCK_RANGE.matcher(hours);
            // 운영 시간이 자유 텍스트일 수 있어 단순한 시간 범위만 종료 판단에 사용한다.
            LocalTime closes = match.matches() ? parseClosing(match.group(1), match.group(2)) : null;
            String schedule = match.matches() || hours.matches("\\d{1,2}:\\d{2}")
                    ? hours : null;
            String image = row.path(seoul ? "MAIN_IMG" : "firstimage").asText().strip();
            return Optional.of(new Event(new BannerEvent(title, region, venue,
                    category(row, seoul), safeImage(image), latitude, longitude,
                    seoul ? "SEOUL" : "TOUR_API", schedule), closes));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static LocalTime parseClosing(String from, String to) {
        try {
            LocalTime start = LocalTime.parse(from, CLOCK);
            LocalTime end = LocalTime.parse(to, CLOCK);
            return end.isAfter(start) ? end : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static LocalDate parseDate(String value, boolean seoul) {
        return seoul ? LocalDate.parse(value.split("[ T]", 2)[0])
                : LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String safeImage(String image) {
        try {
            // 관광공사 응답의 레거시 HTTP 이미지는 같은 호스트의 HTTPS 주소로 표시한다.
            URI uri = URI.create(image.replaceFirst("^http://(?=tong\\.visitkorea\\.or\\.kr/)", "https://"));
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null ? uri.toASCIIString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String category(JsonNode row, boolean seoul) {
        String code = row.path(seoul ? "CODENAME" : "cat3").asText();
        if (code.contains("전시") || "A02080300".equals(code)) return "EXHIBITION";
        if (code.contains("축제") || code.startsWith("A0207")) return "FESTIVAL";
        if (List.of("콘서트", "클래식", "국악", "연극", "뮤지컬/오페라", "무용",
                "A02080500", "A02080600", "A02080900", "A02081000").contains(code)) return "PERFORMANCE";
        return "OTHER";
    }

    private static void requireBody(JsonNode response) {
        if (response == null) throw invalidResponse();
    }

    private static PublicDataClientException invalidResponse() {
        return new PublicDataClientException("행사 공급자가 유효한 목록을 반환하지 않았습니다.");
    }

    public record BannerEvent(String title, String region, String venue, String category, String imageUrl,
                              double latitude, double longitude, String source, String schedule) {}
    public record Result(List<BannerEvent> events, boolean partial) {}
    record Event(BannerEvent banner, LocalTime closes) {}
    private record Snapshot(LocalDate date, ZonedDateTime expires, Optional<List<Event>> events) {}
}
