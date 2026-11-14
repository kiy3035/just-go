package com.example.justgo.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RouteRecommendationClient {
    static final String CANDIDATE_PATH = "/api/v1/places/candidates";
    static final String RECOMMENDATION_PATH = "/api/v1/routes/recommendations";
    private static final int MAX_RESPONSE_CHARACTERS = 65_536;
    private static final int MAX_CANDIDATE_NAME_LENGTH = 100;
    private static final int MAX_SOURCE_CATEGORY_LENGTH = 200;
    private static final Set<String> THEMES = Set.of(
            "CITY", "MOUNTAIN", "RIVER", "LAKE", "SEA", "PARK", "ANYWHERE", "WATER");
    private static final Set<String> CANDIDATE_THEMES = Set.of(
            "CITY", "MOUNTAIN", "RIVER", "LAKE", "SEA", "PARK");
    private static final Set<String> CANDIDATE_SOURCES = Set.of(
            "VERIFIED_DB", "PUBLIC_PARK", "TOUR_API", "TMAP_POI");
    private static final Set<String> EXTERNAL_SOURCES = Set.of(
            "PUBLIC_PARK", "TOUR_API", "TMAP_POI");
    private final String baseUrl;
    private final AtomicReference<HttpURLConnection> activeConnection = new AtomicReference<>();

    public RouteRecommendationClient(String baseUrl) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    public Recommendation recommend(double latitude, double longitude, int minutes,
                                    String tripType, String theme) throws IOException {
        return recommend(latitude, longitude, minutes, tripType, theme, null);
    }

    /** 메인 행사 배너는 코스 추천과 별개의 읽기 요청을 사용한다. */
    public TodayEvents todayEvents() throws IOException {
        HttpURLConnection connection = open("/api/v1/events/today");
        try {
            configure(connection, 75_000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new EventHttpFailure(status);
            }
            JSONObject body = readJson(connection.getInputStream());
            JSONArray rows = body.getJSONArray("events");
            if (rows.length() > 5) throw new InvalidResponse();
            List<BannerEvent> events = new ArrayList<>();
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                double latitude = row.getDouble("latitude");
                double longitude = row.getDouble("longitude");
                if (!(latitude >= 36.8 && latitude <= 38.4 && longitude >= 126 && longitude <= 128)) {
                    throw new InvalidResponse();
                }
                events.add(new BannerEvent(row.getString("title"), row.getString("region"),
                        row.getString("venue"), row.getString("category"),
                        row.isNull("imageUrl") ? "" : row.getString("imageUrl"),
                        latitude, longitude, row.getString("source"),
                        row.isNull("schedule") ? "" : row.getString("schedule")));
            }
            return new TodayEvents(List.copyOf(events), body.getBoolean("partial"));
        } catch (JSONException exception) {
            throw new InvalidResponse();
        } finally {
            connection.disconnect();
        }
    }

    public record BannerEvent(String title, String region, String venue, String category, String imageUrl,
                              double latitude, double longitude, String source, String schedule) { }
    public record TodayEvents(List<BannerEvent> events, boolean partial) { }

    public static final class EventHttpFailure extends IOException {
        private final int status;

        EventHttpFailure(int status) { this.status = status; }

        public int status() { return status; }
    }

    public CandidateList findCandidates(double latitude, double longitude, int minutes,
                                        String tripType, String theme, int limit) throws IOException {
        String query = "?latitude=" + encode(Double.toString(latitude))
                + "&longitude=" + encode(Double.toString(longitude))
                + "&desiredMinutes=" + minutes
                + "&tripType=" + encode(tripType)
                + "&theme=" + encode(theme)
                + "&limit=" + limit;
        HttpURLConnection connection = open(CANDIDATE_PATH + query);
        activeConnection.set(connection);
        try {
            connection.setRequestMethod("GET");
            configure(connection, 20_000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw readHttpError(connection, status);
            }
            return parseCandidates(readJson(connection.getInputStream()), theme);
        } catch (JSONException | IllegalArgumentException exception) {
            throw new InvalidResponse();
        } finally {
            activeConnection.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    public Recommendation recommend(double latitude, double longitude, int minutes,
                                    String tripType, String theme, String candidateToken)
            throws IOException {
        HttpURLConnection connection = open(RECOMMENDATION_PATH);
        activeConnection.set(connection);
        try {
            connection.setRequestMethod("POST");
            configure(connection, 120_000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);
            JSONObject request = new JSONObject()
                    .put("latitude", latitude).put("longitude", longitude)
                    .put("desiredMinutes", minutes).put("tripType", tripType).put("theme", theme);
            if (candidateToken != null) {
                request.put("candidateToken", candidateToken);
            }
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw readHttpError(connection, status);
            }
            return parse(readJson(connection.getInputStream()), theme);
        } catch (JSONException | IllegalArgumentException | java.time.DateTimeException exception) {
            throw new InvalidResponse();
        } finally {
            activeConnection.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    /** 화면 종료나 주행 제한 시 진행 중 로컬 백엔드 요청의 소켓을 닫습니다. */
    public void cancelActiveRequest() {
        HttpURLConnection connection = activeConnection.getAndSet(null);
        if (connection != null) {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String path) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IOException("추천 서버 주소가 비어 있습니다.");
        }
        return (HttpURLConnection) new URL(baseUrl + path).openConnection(Proxy.NO_PROXY);
    }

    private void configure(HttpURLConnection connection, int readTimeoutMillis) {
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestProperty("Accept", "application/json");
    }

    private JSONObject readJson(InputStream input) throws IOException, JSONException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                json.append(buffer, 0, count);
                if (json.length() > MAX_RESPONSE_CHARACTERS) {
                    throw new InvalidResponse();
                }
            }
            return new JSONObject(json.toString());
        }
    }

    private HttpError readHttpError(HttpURLConnection connection, int status) {
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            return new HttpError(status, null, null);
        }
        try {
            JSONObject error = readJson(errorStream);
            return new HttpError(
                    status,
                    error.optString("code", null),
                    error.optString("message", null));
        } catch (IOException | JSONException exception) {
            return new HttpError(status, null, null);
        }
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private CandidateList parseCandidates(JSONObject json, String expectedTheme)
            throws JSONException, InvalidResponse {
        JSONArray candidatesJson = json.getJSONArray("candidates");
        int count = json.getInt("count");
        String requestedTheme = json.getString("theme");
        int tokenExpiresInMinutes = json.getInt("tokenExpiresInMinutes");
        if (count != candidatesJson.length() || count < 0 || count > 50
                || !THEMES.contains(requestedTheme) || !requestedTheme.equals(expectedTheme)
                || tokenExpiresInMinutes != 30) {
            throw new InvalidResponse();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < candidatesJson.length(); index++) {
            JSONObject item = candidatesJson.getJSONObject(index);
            String token = item.getString("candidateToken");
            String name = item.getString("name");
            String theme = item.getString("theme");
            String source = item.getString("source");
            String sourceCategory = item.getString("sourceCategory");
            double latitude = item.getDouble("latitude");
            double longitude = item.getDouble("longitude");
            long distanceMeters = item.getLong("straightLineDistanceMeters");
            if (!isUuid(token) || name.isBlank() || name.length() > MAX_CANDIDATE_NAME_LENGTH
                    || !CANDIDATE_THEMES.contains(theme)
                    || !matchesRequestedTheme(expectedTheme, theme)
                    || !CANDIDATE_SOURCES.contains(source) || sourceCategory.isBlank()
                    || sourceCategory.length() > MAX_SOURCE_CATEGORY_LENGTH
                    || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                    || distanceMeters < 0) {
                throw new InvalidResponse();
            }
            candidates.add(new Candidate(
                    token,
                    name,
                    theme,
                    latitude,
                    longitude,
                    distanceMeters,
                    source,
                    sourceCategory));
        }
        JSONArray unavailableJson = json.getJSONArray("unavailableSources");
        List<String> unavailableSources = new ArrayList<>();
        for (int index = 0; index < unavailableJson.length(); index++) {
            String unavailableSource = unavailableJson.getString(index);
            if (!EXTERNAL_SOURCES.contains(unavailableSource)
                    || unavailableSources.contains(unavailableSource)) {
                throw new InvalidResponse();
            }
            unavailableSources.add(unavailableSource);
        }
        boolean partial = json.getBoolean("partial");
        if (partial != !unavailableSources.isEmpty()) {
            throw new InvalidResponse();
        }
        return new CandidateList(
                requestedTheme,
                tokenExpiresInMinutes,
                partial,
                List.copyOf(unavailableSources),
                List.copyOf(candidates));
    }

    private Recommendation parse(JSONObject json, String expectedTheme)
            throws JSONException, InvalidResponse {
        Mode mode = Mode.valueOf(json.getString("mode"));
        JSONArray routesJson = json.getJSONArray("routes");
        JSONArray alternativesJson = json.optJSONArray("alternativeRoutes");
        if (json.getBoolean("mock") != (mode == Mode.MOCK)
                || (mode == Mode.UNAVAILABLE && routesJson.length() != 0)
                || (mode != Mode.LIVE && alternativesJson != null && alternativesJson.length() != 0)) {
            throw new InvalidResponse();
        }
        boolean requireNavigationStops = mode == Mode.LIVE;
        return new Recommendation(
                mode,
                parseRoutes(routesJson, requireNavigationStops, expectedTheme),
                alternativesJson == null
                        ? List.of()
                        : parseRoutes(alternativesJson, requireNavigationStops, null),
                json.optString("notice", ""));
    }

    private List<Route> parseRoutes(JSONArray routesJson, boolean requireNavigationStops,
                                   String expectedTheme)
            throws JSONException, InvalidResponse {
        if (routesJson.length() > 10) throw new InvalidResponse();
        List<Route> routes = new ArrayList<>();
        for (int index = 0; index < routesJson.length(); index++) {
            JSONObject route = routesJson.getJSONObject(index);
            String routeId = route.getString("routeId");
            String title = route.getString("title");
            String theme = route.getString("theme");
            int estimatedMinutes = route.getInt("estimatedMinutes");
            long durationSeconds = route.optLong("durationSeconds", estimatedMinutes * 60L);
            long distanceMeters = route.getLong("distanceMeters");
            if (routeId.isBlank() || title.isBlank() || !THEMES.contains(theme)
                    || !matchesRequestedTheme(expectedTheme, theme)
                    || estimatedMinutes < 0 || durationSeconds < 0 || distanceMeters < 0
                    || estimatedMinutes != wholeMinutes(durationSeconds)) {
                throw new InvalidResponse();
            }
            JSONArray stopsJson = route.getJSONArray("stops");
            if (stopsJson.length() > 3
                    || (requireNavigationStops && stopsJson.length() == 0)) {
                throw new InvalidResponse();
            }
            List<Stop> stops = new ArrayList<>();
            Set<Integer> stopSequences = new HashSet<>();
            for (int stopIndex = 0; stopIndex < stopsJson.length(); stopIndex++) {
                JSONObject stop = stopsJson.getJSONObject(stopIndex);
                int sequence = stop.getInt("sequence");
                String name = stop.getString("name");
                double latitude = stop.getDouble("latitude");
                double longitude = stop.getDouble("longitude");
                if (sequence < 1 || !stopSequences.add(sequence) || name.isBlank()
                        || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                        || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                    throw new InvalidResponse();
                }
                stops.add(new Stop(sequence, StopType.valueOf(stop.getString("type")),
                        name, latitude, longitude));
            }
            stops.sort(Comparator.comparingInt(Stop::sequence));
            validateStopOrder(stops, requireNavigationStops);
            Integer repeatedSegmentPercent = route.isNull("repeatedSegmentPercent")
                    ? null
                    : route.getInt("repeatedSegmentPercent");
            if (repeatedSegmentPercent != null
                    && (repeatedSegmentPercent < 0 || repeatedSegmentPercent > 100)) {
                throw new InvalidResponse();
            }
            List<Long> legSeconds = new ArrayList<>();
            JSONArray legs = route.optJSONArray("legSeconds");
            if (legs != null && legs.length() > 0) {
                // 측정된 각 구간은 내비에 넘기는 순서대로 지점 하나에 대응한다.
                if (legs.length() != stops.size()) throw new InvalidResponse();
                long measuredSeconds = 0;
                for (int leg = 0; leg < legs.length(); leg++) {
                    long seconds = legs.getLong(leg);
                    if (seconds <= 0 || seconds > durationSeconds - measuredSeconds) {
                        throw new InvalidResponse();
                    }
                    measuredSeconds += seconds;
                    legSeconds.add(seconds);
                }
                if (measuredSeconds != durationSeconds) throw new InvalidResponse();
            }
            JSONObject eventJson = route.optJSONObject("event");
            Event event = eventJson == null ? null : new Event(
                    eventJson.getString("name"), eventJson.getString("venue"),
                    java.time.ZonedDateTime.parse(eventJson.getString("opensAt")),
                    java.time.ZonedDateTime.parse(eventJson.getString("closesAt")),
                    java.time.ZonedDateTime.parse(eventJson.getString("arrivesAt")));
            routes.add(new Route(routeId, title,
                    estimatedMinutes, durationSeconds, distanceMeters,
                    theme, repeatedSegmentPercent, List.copyOf(stops),
                    route.optString("reason", ""), List.copyOf(legSeconds), event));
        }
        return routes;
    }

    private boolean matchesRequestedTheme(String requestedTheme, String actualTheme) {
        if (requestedTheme == null || "ANYWHERE".equals(requestedTheme)) return true;
        if ("WATER".equals(requestedTheme)) {
            return "WATER".equals(actualTheme)
                    || "RIVER".equals(actualTheme)
                    || "LAKE".equals(actualTheme);
        }
        return requestedTheme.equals(actualTheme);
    }

    private long wholeMinutes(long durationSeconds) {
        return durationSeconds / 60L + (durationSeconds % 60L == 0 ? 0 : 1);
    }

    /** LIVE 경로가 Pleos의 최대 두 경유지와 마지막 목적지 계약을 만족하는지 확인합니다. */
    private void validateStopOrder(List<Stop> stops, boolean requireNavigationStops)
            throws InvalidResponse {
        if (stops.isEmpty()) {
            if (requireNavigationStops) throw new InvalidResponse();
            return;
        }
        int destinationCount = 0;
        for (int index = 0; index < stops.size(); index++) {
            Stop stop = stops.get(index);
            if (stop.sequence() != index + 1) throw new InvalidResponse();
            if (stop.type() == StopType.DESTINATION) {
                destinationCount++;
                if (index != stops.size() - 1) throw new InvalidResponse();
            }
        }
        if (destinationCount != 1) throw new InvalidResponse();
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public enum Mode { MOCK, LIVE, UNAVAILABLE }
    public enum StopType { WAYPOINT, DESTINATION }
    public record Candidate(String token, String name, String theme,
                            double latitude, double longitude, long distanceMeters,
                            String source, String sourceCategory) { }
    public record CandidateList(String theme, int tokenExpiresInMinutes, boolean partial,
                                List<String> unavailableSources,
                                List<Candidate> candidates) {
        public boolean sourceUnavailable() {
            return partial && candidates.isEmpty();
        }
    }
    public record Stop(int sequence, StopType type, String name,
                       double latitude, double longitude) { }
    public record Route(String routeId, String title, int minutes, long durationSeconds, long meters,
                        String theme, Integer repeatedSegmentPercent,
                        List<Stop> stops, String reason, List<Long> legSeconds, Event event) { }
    public record Event(String name, String venue, java.time.ZonedDateTime opensAt,
                        java.time.ZonedDateTime closesAt, java.time.ZonedDateTime arrivesAt) { }
    public record Recommendation(Mode mode, List<Route> routes,
                                 List<Route> alternativeRoutes, String notice) { }

    public static final class HttpError extends IOException {
        public final int status;
        public final String code;

        HttpError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    public static final class InvalidResponse extends IOException { }
}
