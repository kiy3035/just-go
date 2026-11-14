package com.example.justgo.backend.integration.routing.tmap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * TMAP 다중 경유지 30 API의 요청·응답과 오류를 내부 모델로 변환하는 HTTP 클라이언트입니다.
 * 원시 경로 응답을 저장하거나 요청 좌표를 로그로 남기지 않습니다.
 */
public class TmapRouteClient {

    private static final String MULTI_ROUTE_PATH = "/tmap/routes/routeSequential30?version=1";
    private static final String SINGLE_ROUTE_PATH = "/tmap/routes?version=1&totalValue=2";
    private static final String WGS84 = "WGS84GEO";
    private static final String RECOMMENDED_ROUTE = "0";
    private static final String PASSENGER_CAR = "1";
    private static final long MIN_AVERAGE_SPEED_KILOMETERS_PER_HOUR = 5L;
    private static final long MAX_AVERAGE_SPEED_KILOMETERS_PER_HOUR = 130L;
    private static final DateTimeFormatter START_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final RestClient restClient;
    private final TmapCallLimiter callLimiter;
    private final Clock clock;
    private final Duration routeCacheTtl;
    private final int routeCacheMaxEntries;
    private final Map<String, CachedRoute> routeCache = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<TmapRouteSummary>> inFlightRoutes =
            new ConcurrentHashMap<>();
    private final TmapRouteShapeAnalyzer shapeAnalyzer = new TmapRouteShapeAnalyzer();

    public TmapRouteClient(
            RestClient restClient,
            TmapCallLimiter callLimiter,
            Clock clock) {
        this(restClient, callLimiter, clock, Duration.ZERO, 0);
    }

    TmapRouteClient(
            RestClient restClient,
            TmapCallLimiter callLimiter,
            Clock clock,
            Duration routeCacheTtl,
            int routeCacheMaxEntries) {
        this.restClient = restClient;
        this.callLimiter = callLimiter;
        this.clock = clock;
        this.routeCacheTtl = routeCacheTtl;
        this.routeCacheMaxEntries = routeCacheMaxEntries;
        if (routeCacheTtl == null || routeCacheTtl.isNegative()) {
            throw new IllegalArgumentException("TMAP 경로 캐시 TTL은 0초 이상이어야 합니다.");
        }
        if (routeCacheTtl.isZero() != (routeCacheMaxEntries == 0)
                || routeCacheMaxEntries < 0) {
            throw new IllegalArgumentException("TMAP 경로 캐시 TTL과 최대 항목 수를 함께 설정해야 합니다.");
        }
    }

    /**
     * 출발·도착·순서 고정 경유지로 실제 도로 총 거리와 주행 시간을 조회합니다.
     * 재시도는 자동 수행하지 않아 한 요청이 여러 무료 쿼터를 소비하지 않게 합니다.
     */
    public TmapRouteSummary findRoute(TmapRouteSearch search) {
        if (search == null) {
            throw new IllegalArgumentException("TMAP 경로 검색 조건이 필요합니다.");
        }
        String cacheKey = routeCacheKey(search);
        TmapRouteSummary cached = cachedRoute(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (routeCacheTtl.isZero()) {
            return requestRoute(search, cacheKey);
        }

        CompletableFuture<TmapRouteSummary> current = new CompletableFuture<>();
        CompletableFuture<TmapRouteSummary> existing = inFlightRoutes.putIfAbsent(
                cacheKey, current);
        if (existing != null) {
            return awaitRoute(existing);
        }

        try {
            TmapRouteSummary cachedAfterRegistration = cachedRoute(cacheKey);
            if (cachedAfterRegistration != null) {
                current.complete(cachedAfterRegistration);
                return cachedAfterRegistration;
            }
            TmapRouteSummary summary = requestRoute(search, cacheKey);
            current.complete(summary);
            return summary;
        } catch (RuntimeException | Error exception) {
            current.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightRoutes.remove(cacheKey, current);
        }
    }

    /** 동일 좌표 조합의 진행 중 외부 호출 하나를 공유해 호출권 중복 소비를 막습니다. */
    private TmapRouteSummary awaitRoute(CompletableFuture<TmapRouteSummary> inFlight) {
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

    private TmapRouteSummary requestRoute(TmapRouteSearch search, String cacheKey) {
        callLimiter.acquire();

        try {
            boolean hasWaypoints = !search.waypoints().isEmpty();
            TmapApiResponse response = restClient.post()
                    .uri(hasWaypoints ? MULTI_ROUTE_PATH : SINGLE_ROUTE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(hasWaypoints ? toMultiApiRequest(search) : toSingleApiRequest(search))
                    .retrieve()
                    .onStatus(status -> status.value() == 204, (request, upstreamResponse) -> {
                        throw new TmapRouteClientException(TmapClientErrorType.NO_ROUTE,
                                "TMAP에서 자동차 경로를 찾지 못했습니다.");
                    })
                    .onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
                        throw mapHttpError(upstreamResponse.getStatusCode());
                    })
                    .body(TmapApiResponse.class);
            TmapRouteSummary summary = validateAndMap(
                    response, search.waypoints().size());
            cacheRoute(cacheKey, summary);
            return summary;
        } catch (TmapRouteClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.NETWORK,
                    "TMAP 네트워크 호출에 실패했습니다.",
                    exception);
        }
    }

    /** 같은 좌표 조합을 다시 눌렀을 때 호출권을 재소비하지 않도록 요약값만 재사용합니다. */
    private TmapRouteSummary cachedRoute(String key) {
        if (routeCacheTtl.isZero()) {
            return null;
        }
        Instant now = Instant.now(clock);
        synchronized (routeCache) {
            routeCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            CachedRoute cached = routeCache.get(key);
            return cached == null ? null : cached.summary();
        }
    }

    private void cacheRoute(String key, TmapRouteSummary summary) {
        if (routeCacheTtl.isZero()) {
            return;
        }
        synchronized (routeCache) {
            while (routeCache.size() >= routeCacheMaxEntries) {
                routeCache.remove(routeCache.keySet().iterator().next());
            }
            routeCache.put(key, new CachedRoute(
                    summary,
                    Instant.now(clock).plus(routeCacheTtl)));
        }
    }

    /** 원시 좌표 대신 출발·도착·경유 순서를 SHA-256으로 축약해 메모리 키로 사용합니다. */
    private String routeCacheKey(TmapRouteSearch search) {
        StringBuilder material = new StringBuilder();
        appendCoordinate(material, search.start());
        appendCoordinate(material, search.end());
        for (TmapWaypoint waypoint : search.waypoints()) {
            appendCoordinate(material, waypoint.coordinate());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void appendCoordinate(StringBuilder material, TmapCoordinate coordinate) {
        material.append(coordinate.latitude().stripTrailingZeros().toPlainString())
                .append(',')
                .append(coordinate.longitude().stripTrailingZeros().toPlainString())
                .append(';');
    }

    /**
     * 내부 좌표의 경도·위도를 TMAP의 X·Y 필드 순서로 변환합니다.
     */
    private TmapMultiApiRequest toMultiApiRequest(TmapRouteSearch search) {
        List<TmapApiWaypoint> waypoints = search.waypoints().stream()
                .map(waypoint -> new TmapApiWaypoint(
                        waypoint.id(),
                        waypoint.name(),
                        waypoint.coordinate().longitude().toPlainString(),
                        waypoint.coordinate().latitude().toPlainString()))
                .toList();

        return new TmapMultiApiRequest(
                WGS84,
                WGS84,
                "출발",
                search.start().longitude().toPlainString(),
                search.start().latitude().toPlainString(),
                ZonedDateTime.now(clock).format(START_TIME_FORMAT),
                "도착",
                search.end().longitude().toPlainString(),
                search.end().latitude().toPlainString(),
                RECOMMENDED_ROUTE,
                PASSENGER_CAR,
                waypoints);
    }

    /** 경유지가 없는 편도 경로를 공식 자동차 경로안내 요청 형식으로 변환합니다. */
    private TmapSingleApiRequest toSingleApiRequest(TmapRouteSearch search) {
        return new TmapSingleApiRequest(
                WGS84,
                WGS84,
                search.start().longitude().toPlainString(),
                search.start().latitude().toPlainString(),
                search.end().longitude().toPlainString(),
                search.end().latitude().toPlainString(),
                Integer.parseInt(RECOMMENDED_ROUTE),
                Integer.parseInt(PASSENGER_CAR));
    }

    /**
     * 인증·쿼터·입력 오류를 구분하되 원시 오류 본문이나 키는 노출하지 않습니다.
     */
    private TmapRouteClientException mapHttpError(HttpStatusCode statusCode) {
        TmapClientErrorType errorType;
        if (statusCode.value() == 401 || statusCode.value() == 403) {
            errorType = TmapClientErrorType.AUTHENTICATION;
        } else if (statusCode.value() == 429) {
            errorType = TmapClientErrorType.QUOTA_EXCEEDED;
        } else if (statusCode.is4xxClientError()) {
            errorType = TmapClientErrorType.INVALID_REQUEST;
        } else {
            errorType = TmapClientErrorType.UPSTREAM_FAILURE;
        }
        return new TmapRouteClientException(errorType, "TMAP 호출이 실패했습니다.");
    }

    /**
     * TMAP의 문자열 숫자를 양수 단위값으로 검증한 뒤 내부 결과로 변환합니다.
     */
    private TmapRouteSummary validateAndMap(
            TmapApiResponse response,
            int waypointCount) {
        if (response == null || !"FeatureCollection".equals(response.type())) {
            throw invalidResponse();
        }
        TmapApiResponseProperties properties = response.properties();
        if (properties == null && response.features() != null && !response.features().isEmpty()) {
            properties = response.features().getFirst().properties();
        }
        if (properties == null) {
            throw invalidResponse();
        }
        try {
            long distanceMeters = Long.parseLong(properties.totalDistance());
            long durationSeconds = Long.parseLong(properties.totalTime());
            if (distanceMeters <= 0 || durationSeconds <= 0) {
                throw invalidResponse();
            }
            validatePlausibleDrivingMetrics(distanceMeters, durationSeconds);
            return new TmapRouteSummary(
                    distanceMeters,
                    durationSeconds,
                    calculateRepeatedSegmentPercent(response.features()),
                    extractLegSeconds(response.features(), waypointCount, durationSeconds));
        } catch (NumberFormatException exception) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.INVALID_RESPONSE,
                    "TMAP 거리 또는 시간 응답 형식이 올바르지 않습니다.",
                    exception);
        }
    }

    /** 공식 경유지·도착점의 구간 시간을 순서대로 읽고 전체 시간과 일치할 때만 사용합니다. */
    private List<Long> extractLegSeconds(
            List<TmapApiFeature> features,
            int waypointCount,
            long durationSeconds) {
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        List<TmapRouteLeg> legs = new ArrayList<>();
        try {
            for (TmapApiFeature feature : features) {
                TmapApiResponseProperties properties = feature.properties();
                if (properties == null || !isLegEndPoint(properties.pointType())) {
                    continue;
                }
                if (properties.index() == null || properties.time() == null) {
                    return List.of();
                }
                long seconds = Long.parseLong(properties.time());
                if (seconds <= 0) {
                    return List.of();
                }
                legs.add(new TmapRouteLeg(properties.index(), seconds));
            }
            if (legs.size() != waypointCount + 1) {
                return List.of();
            }
            legs.sort(Comparator.comparingInt(TmapRouteLeg::index));
            if (new HashSet<>(legs.stream().map(TmapRouteLeg::index).toList()).size()
                    != legs.size()) {
                return List.of();
            }
            long measuredSeconds = 0L;
            for (TmapRouteLeg leg : legs) {
                measuredSeconds = Math.addExact(measuredSeconds, leg.seconds());
            }
            if (measuredSeconds != durationSeconds) {
                return List.of();
            }
            return legs.stream().map(TmapRouteLeg::seconds).toList();
        } catch (NumberFormatException | ArithmeticException exception) {
            return List.of();
        }
    }

    private boolean isLegEndPoint(String pointType) {
        if ("E".equals(pointType)) {
            return true;
        }
        if (pointType == null || pointType.length() < 2 || pointType.charAt(0) != 'B') {
            return false;
        }
        for (int index = 1; index < pointType.length(); index++) {
            if (!Character.isDigit(pointType.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /** 거리와 시간이 평균 시속 5~130km 범위를 벗어나면 추천용 자동차 경로에서 제외합니다. */
    private void validatePlausibleDrivingMetrics(long distanceMeters, long durationSeconds) {
        BigInteger hourlyDistance = BigInteger.valueOf(distanceMeters)
                .multiply(BigInteger.valueOf(3_600L));
        BigInteger minimumDistance = BigInteger.valueOf(durationSeconds)
                .multiply(BigInteger.valueOf(MIN_AVERAGE_SPEED_KILOMETERS_PER_HOUR * 1_000L));
        BigInteger maximumDistance = BigInteger.valueOf(durationSeconds)
                .multiply(BigInteger.valueOf(MAX_AVERAGE_SPEED_KILOMETERS_PER_HOUR * 1_000L));
        if (hourlyDistance.compareTo(minimumDistance) < 0
                || hourlyDistance.compareTo(maximumDistance) > 0) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.IMPLAUSIBLE_ROUTE,
                    "TMAP 거리와 시간의 조합이 추천 가능한 자동차 경로 범위를 벗어났습니다.");
        }
    }

    private TmapRouteClientException invalidResponse() {
        return new TmapRouteClientException(
                TmapClientErrorType.INVALID_RESPONSE,
                "TMAP 응답에 유효한 거리와 시간이 없습니다.");
    }

    /** GeoJSON LineString만 읽어 원시 좌표를 남기지 않고 왕복 중복 지표로 축약합니다. */
    private Integer calculateRepeatedSegmentPercent(List<TmapApiFeature> features) {
        if (features == null || features.isEmpty()) {
            return null;
        }

        List<List<TmapCoordinate>> lineStrings = new ArrayList<>();
        for (TmapApiFeature feature : features) {
            if (feature.geometry() == null
                    || !"LineString".equals(feature.geometry().type())) {
                continue;
            }
            List<TmapCoordinate> lineString = toLineString(feature.geometry().coordinates());
            if (lineString.size() >= 2) {
                lineStrings.add(lineString);
            }
        }
        return shapeAnalyzer.calculateRepeatedSegmentPercent(lineStrings);
    }

    /** 공식 GeoJSON의 [경도, 위도] 배열을 내부 [위도, 경도] 의미로 안전하게 변환합니다. */
    private List<TmapCoordinate> toLineString(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray()) {
            return List.of();
        }

        List<TmapCoordinate> lineString = new ArrayList<>();
        try {
            for (JsonNode coordinate : coordinates) {
                if (!coordinate.isArray()
                        || coordinate.size() < 2
                        || !coordinate.get(0).isNumber()
                        || !coordinate.get(1).isNumber()) {
                    return List.of();
                }
                BigDecimal longitude = coordinate.get(0).decimalValue();
                BigDecimal latitude = coordinate.get(1).decimalValue();
                lineString.add(new TmapCoordinate(latitude, longitude));
            }
            return List.copyOf(lineString);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private record CachedRoute(TmapRouteSummary summary, Instant expiresAt) {
    }

    private record TmapMultiApiRequest(
            String reqCoordType,
            String resCoordType,
            String startName,
            String startX,
            String startY,
            String startTime,
            String endName,
            String endX,
            String endY,
            String searchOption,
            String carType,
            List<TmapApiWaypoint> viaPoints) {
    }

    private record TmapSingleApiRequest(
            String reqCoordType,
            String resCoordType,
            String startX,
            String startY,
            String endX,
            String endY,
            int searchOption,
            int carType) {
    }

    private record TmapApiWaypoint(
            String viaPointId,
            String viaPointName,
            String viaX,
            String viaY) {
    }

    private record TmapApiResponse(
            String type,
            TmapApiResponseProperties properties,
            List<TmapApiFeature> features) {
    }

    private record TmapApiFeature(
            TmapApiGeometry geometry,
            TmapApiResponseProperties properties) {
    }

    private record TmapApiGeometry(String type, JsonNode coordinates) {
    }

    private record TmapApiResponseProperties(
            String totalDistance,
            String totalTime,
            Integer index,
            String pointType,
            String time) {
    }

    private record TmapRouteLeg(int index, long seconds) {
    }
}
