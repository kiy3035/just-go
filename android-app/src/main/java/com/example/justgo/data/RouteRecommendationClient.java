package com.example.justgo.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RouteRecommendationClient {
    private final String baseUrl;

    public RouteRecommendationClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Recommendation recommend(double latitude, double longitude, int minutes,
                                    String tripType, String theme) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                baseUrl + "/api/v1/routes/recommendations").openConnection(Proxy.NO_PROXY);
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(45_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] body = new JSONObject()
                    .put("latitude", latitude).put("longitude", longitude)
                    .put("desiredMinutes", minutes).put("tripType", tripType).put("theme", theme)
                    .toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpError(status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    json.append(buffer, 0, count);
                    if (json.length() > 65_536) {
                        throw new InvalidResponse();
                    }
                }
                return parse(new JSONObject(json.toString()));
            }
        } catch (JSONException | IllegalArgumentException exception) {
            throw new InvalidResponse();
        } finally {
            connection.disconnect();
        }
    }

    private Recommendation parse(JSONObject json) throws JSONException, InvalidResponse {
        Mode mode = Mode.valueOf(json.getString("mode"));
        JSONArray routesJson = json.getJSONArray("routes");
        if (json.getBoolean("mock") != (mode == Mode.MOCK)
                || (mode == Mode.UNAVAILABLE && routesJson.length() != 0)) {
            throw new InvalidResponse();
        }
        List<Route> routes = new ArrayList<>();
        for (int index = 0; index < routesJson.length(); index++) {
            JSONObject route = routesJson.getJSONObject(index);
            JSONArray stopsJson = route.getJSONArray("stops");
            List<Stop> stops = new ArrayList<>();
            for (int stopIndex = 0; stopIndex < stopsJson.length(); stopIndex++) {
                JSONObject stop = stopsJson.getJSONObject(stopIndex);
                stops.add(new Stop(stop.getInt("sequence"), StopType.valueOf(stop.getString("type")),
                        stop.getString("name")));
            }
            stops.sort(Comparator.comparingInt(Stop::sequence));
            routes.add(new Route(route.getString("title"), route.getInt("estimatedMinutes"),
                    route.getLong("distanceMeters"), route.getString("reason"), stops));
        }
        JSONArray suggestionsJson = json.getJSONArray("suggestions");
        List<Suggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < suggestionsJson.length(); index++) {
            JSONObject suggestion = suggestionsJson.getJSONObject(index);
            suggestions.add(new Suggestion(suggestion.getInt("desiredMinutes"),
                    suggestion.getString("tripType"), suggestion.getString("theme"),
                    suggestion.getString("reason")));
        }
        return new Recommendation(mode, routes, suggestions, json.getString("notice"));
    }

    public enum Mode { MOCK, LIVE, UNAVAILABLE }
    public enum StopType { WAYPOINT, DESTINATION }
    public record Stop(int sequence, StopType type, String name) { }
    public record Route(String title, int minutes, long meters, String reason, List<Stop> stops) { }
    public record Suggestion(int minutes, String tripType, String theme, String reason) { }
    public record Recommendation(Mode mode, List<Route> routes, List<Suggestion> suggestions,
                                 String notice) { }

    public static final class HttpError extends IOException {
        public final int status;

        HttpError(int status) {
            this.status = status;
        }
    }

    public static final class InvalidResponse extends IOException { }
}
