package com.example.justgo.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RouteRecommendationClient {
    static final String CANDIDATE_PATH = "/api/v1/places/candidates";
    static final String RECOMMENDATION_PATH = "/api/v1/routes/recommendations";
    private static final int MAX_RESPONSE_CHARACTERS = 65_536;
    private final String baseUrl;

    public RouteRecommendationClient(String baseUrl) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    public Recommendation recommend(double latitude, double longitude, int minutes,
                                    String tripType, String theme) throws IOException {
        return recommend(latitude, longitude, minutes, tripType, theme, null);
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
        try {
            connection.setRequestMethod("GET");
            configure(connection, 20_000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpError(status);
            }
            return parseCandidates(readJson(connection));
        } catch (JSONException | IllegalArgumentException exception) {
            throw new InvalidResponse();
        } finally {
            connection.disconnect();
        }
    }

    public Recommendation recommend(double latitude, double longitude, int minutes,
                                    String tripType, String theme, String candidateToken)
            throws IOException {
        HttpURLConnection connection = open(RECOMMENDATION_PATH);
        try {
            connection.setRequestMethod("POST");
            configure(connection, 45_000);
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
                throw new HttpError(status);
            }
            return parse(readJson(connection));
        } catch (JSONException | IllegalArgumentException exception) {
            throw new InvalidResponse();
        } finally {
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

    private JSONObject readJson(HttpURLConnection connection) throws IOException, JSONException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
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

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private CandidateList parseCandidates(JSONObject json) throws JSONException, InvalidResponse {
        JSONArray candidatesJson = json.getJSONArray("candidates");
        if (json.getInt("count") != candidatesJson.length()) {
            throw new InvalidResponse();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < candidatesJson.length(); index++) {
            JSONObject item = candidatesJson.getJSONObject(index);
            double latitude = item.getDouble("latitude");
            double longitude = item.getDouble("longitude");
            long distanceMeters = item.getLong("straightLineDistanceMeters");
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                    || distanceMeters < 0) {
                throw new InvalidResponse();
            }
            candidates.add(new Candidate(
                    item.getString("candidateToken"),
                    item.getString("name"),
                    item.getString("theme"),
                    latitude,
                    longitude,
                    distanceMeters,
                    item.getString("source"),
                    item.getString("sourceCategory")));
        }
        JSONArray unavailableJson = json.getJSONArray("unavailableSources");
        List<String> unavailableSources = new ArrayList<>();
        for (int index = 0; index < unavailableJson.length(); index++) {
            unavailableSources.add(unavailableJson.getString(index));
        }
        return new CandidateList(
                json.getString("theme"),
                json.getInt("tokenExpiresInMinutes"),
                json.getBoolean("partial"),
                List.copyOf(unavailableSources),
                List.copyOf(candidates));
    }

    private Recommendation parse(JSONObject json) throws JSONException, InvalidResponse {
        Mode mode = Mode.valueOf(json.getString("mode"));
        JSONArray routesJson = json.getJSONArray("routes");
        JSONArray alternativesJson = json.optJSONArray("alternativeRoutes");
        if (json.getBoolean("mock") != (mode == Mode.MOCK)
                || (mode == Mode.UNAVAILABLE && routesJson.length() != 0)
                || (mode != Mode.LIVE && alternativesJson != null && alternativesJson.length() != 0)) {
            throw new InvalidResponse();
        }
        return new Recommendation(mode, parseRoutes(routesJson),
                alternativesJson == null ? List.of() : parseRoutes(alternativesJson));
    }

    private List<Route> parseRoutes(JSONArray routesJson) throws JSONException, InvalidResponse {
        List<Route> routes = new ArrayList<>();
        for (int index = 0; index < routesJson.length(); index++) {
            JSONObject route = routesJson.getJSONObject(index);
            JSONArray stopsJson = route.getJSONArray("stops");
            List<Stop> stops = new ArrayList<>();
            for (int stopIndex = 0; stopIndex < stopsJson.length(); stopIndex++) {
                JSONObject stop = stopsJson.getJSONObject(stopIndex);
                double latitude = stop.getDouble("latitude");
                double longitude = stop.getDouble("longitude");
                if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                        || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                    throw new InvalidResponse();
                }
                stops.add(new Stop(stop.getInt("sequence"), StopType.valueOf(stop.getString("type")),
                        stop.getString("name"), latitude, longitude));
            }
            stops.sort(Comparator.comparingInt(Stop::sequence));
            routes.add(new Route(route.getString("routeId"), route.getString("title"),
                    route.getInt("estimatedMinutes"), route.getLong("distanceMeters"),
                    route.getString("theme"), stops));
        }
        return routes;
    }

    public enum Mode { MOCK, LIVE, UNAVAILABLE }
    public enum StopType { WAYPOINT, DESTINATION }
    public record Candidate(String token, String name, String theme,
                            double latitude, double longitude, long distanceMeters,
                            String source, String sourceCategory) { }
    public record CandidateList(String theme, int tokenExpiresInMinutes, boolean partial,
                                List<String> unavailableSources,
                                List<Candidate> candidates) { }
    public record Stop(int sequence, StopType type, String name,
                       double latitude, double longitude) { }
    public record Route(String routeId, String title, int minutes, long meters,
                        String theme, List<Stop> stops) { }
    public record Recommendation(Mode mode, List<Route> routes, List<Route> alternativeRoutes) { }

    public static final class HttpError extends IOException {
        public final int status;

        HttpError(int status) {
            this.status = status;
        }
    }

    public static final class InvalidResponse extends IOException { }
}
