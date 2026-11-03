package com.example.justgo.backend.integration.routing.tmap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    private static final DateTimeFormatter START_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final RestClient restClient;
    private final TmapDailyCallLimiter callLimiter;
    private final Clock clock;
    private final TmapRouteShapeAnalyzer shapeAnalyzer = new TmapRouteShapeAnalyzer();

    public TmapRouteClient(
            RestClient restClient,
            TmapDailyCallLimiter callLimiter,
            Clock clock) {
        this.restClient = restClient;
        this.callLimiter = callLimiter;
        this.clock = clock;
    }

    /**
     * 출발·도착·순서 고정 경유지로 실제 도로 총 거리와 주행 시간을 조회합니다.
     * 재시도는 자동 수행하지 않아 한 요청이 여러 무료 쿼터를 소비하지 않게 합니다.
     */
    public TmapRouteSummary findRoute(TmapRouteSearch search) {
        if (search == null) {
            throw new IllegalArgumentException("TMAP 경로 검색 조건이 필요합니다.");
        }
        callLimiter.acquire();

        try {
            boolean hasWaypoints = !search.waypoints().isEmpty();
            TmapApiResponse response = restClient.post()
                    .uri(hasWaypoints ? MULTI_ROUTE_PATH : SINGLE_ROUTE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(hasWaypoints ? toMultiApiRequest(search) : toSingleApiRequest(search))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstreamResponse) -> {
                        throw mapHttpError(upstreamResponse.getStatusCode());
                    })
                    .body(TmapApiResponse.class);
            return validateAndMap(response);
        } catch (TmapRouteClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.NETWORK,
                    "TMAP 네트워크 호출에 실패했습니다.",
                    exception);
        }
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
    private TmapRouteSummary validateAndMap(TmapApiResponse response) {
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
            return new TmapRouteSummary(
                    distanceMeters,
                    durationSeconds,
                    calculateRepeatedSegmentPercent(response.features()));
        } catch (NumberFormatException exception) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.INVALID_RESPONSE,
                    "TMAP 거리 또는 시간 응답 형식이 올바르지 않습니다.",
                    exception);
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

    private record TmapApiResponseProperties(String totalDistance, String totalTime) {
    }
}
