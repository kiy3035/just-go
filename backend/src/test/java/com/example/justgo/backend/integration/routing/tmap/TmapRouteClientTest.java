package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 TMAP 쿼터를 소비하지 않고 HTTP 계약과 안전장치를 검증합니다.
 */
class TmapRouteClientTest {

    private static final String BASE_URL = "https://tmap.test.invalid";
    private static final String TEST_APP_KEY = "TEST_APP_KEY_ONLY";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-01T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    /**
     * appKey 헤더, WGS84 X/Y 순서와 거리·초 단위 응답 변환을 확인합니다.
     */
    @Test
    void sendsOfficialRequestShapeAndParsesSummary() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("appKey", TEST_APP_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 90);

        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("appKey", TEST_APP_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "reqCoordType": "WGS84GEO",
                          "resCoordType": "WGS84GEO",
                          "startX": "0.200000",
                          "startY": "0.100000",
                          "startTime": "202611010900",
                          "endX": "0.400000",
                          "endY": "0.300000",
                          "searchOption": "0",
                          "carType": "1",
                          "viaPoints": [
                            {
                              "viaPointId": "7",
                              "viaPointName": "TEST_WAYPOINT_ONLY",
                              "viaX": "0.600000",
                              "viaY": "0.500000"
                            }
                          ]
                        }
                        """, JsonCompareMode.LENIENT))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "properties": {
                            "totalDistance": "7271",
                            "totalTime": "2220",
                            "totalFare": "0"
                          },
                          "features": [
                            {
                              "geometry": {
                                "type": "LineString",
                                "coordinates": [[0.0, 0.0], [0.01, 0.0], [0.02, 0.0]]
                              },
                              "properties": {"index": "1"}
                            },
                            {
                              "geometry": {
                                "type": "LineString",
                                "coordinates": [[0.02, 0.0], [0.01, 0.0], [0.0, 0.0]]
                              },
                              "properties": {"index": "2"}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmapRouteSummary result = client.findRoute(testSearch());

        assertThat(result.distanceMeters()).isEqualTo(7271L);
        assertThat(result.durationSeconds()).isEqualTo(2220L);
        assertThat(result.repeatedSegmentPercent()).isEqualTo(50);
        server.verify();
    }

    /**
     * TMAP의 429 응답을 무료 쿼터 초과 오류로 분류하는지 확인합니다.
     */
    @Test
    void classifiesUpstreamQuotaError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 90);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.findRoute(testSearch()))
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
        server.verify();
    }

    @Test
    void classifiesNoContentAsNoRoute() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 90);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> client.findRoute(testSearch()))
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType()).isEqualTo(TmapClientErrorType.NO_ROUTE));
        server.verify();
    }

    /**
     * 설정된 로컬 일일 상한 이후에는 외부 요청을 추가로 보내지 않는지 확인합니다.
     */
    @Test
    void blocksCallsAboveLocalDailyLimit() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 1);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "properties": {"totalDistance": "100", "totalTime": "60"}
                        }
                        """, MediaType.APPLICATION_JSON));

        client.findRoute(testSearch());

        assertThatThrownBy(() -> client.findRoute(testSearch()))
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
        server.verify();
    }

    /** 동일 경로 재요청은 요약 캐시를 사용하고 다른 좌표만 새 호출권을 요구하는지 확인합니다. */
    @Test
    void reusesCachedSummaryWithoutConsumingAnotherCall() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = new TmapRouteClient(
                builder.build(),
                new TmapDailyCallLimiter(1, FIXED_CLOCK),
                FIXED_CLOCK,
                Duration.ofMinutes(10),
                16);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "properties": {"totalDistance": "100", "totalTime": "60"}
                        }
                        """, MediaType.APPLICATION_JSON));

        TmapRouteSummary first = client.findRoute(testSearch());
        TmapRouteSummary cached = client.findRoute(testSearch());
        TmapRouteSearch differentDestination = new TmapRouteSearch(
                testSearch().start(),
                new TmapCoordinate(new BigDecimal("0.310000"), new BigDecimal("0.410000")),
                testSearch().waypoints());

        assertThat(cached).isEqualTo(first);
        assertThatThrownBy(() -> client.findRoute(differentDestination))
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
        server.verify();
    }

    /** 동시에 들어온 동일 경로 요청도 한 번의 외부 호출과 호출권을 공유하는지 확인합니다. */
    @Test
    void sharesOneUpstreamCallForConcurrentIdenticalRoutes() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = new TmapRouteClient(
                builder.build(),
                new TmapDailyCallLimiter(1, FIXED_CLOCK),
                FIXED_CLOCK,
                Duration.ofMinutes(10),
                16);
        CountDownLatch upstreamStarted = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(request -> {
                    upstreamStarted.countDown();
                    try {
                        if (!releaseUpstream.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("TEST_UPSTREAM_RELEASE_TIMEOUT");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("TEST_UPSTREAM_INTERRUPTED", exception);
                    }
                    return withSuccess("""
                            {
                              "type": "FeatureCollection",
                              "properties": {"totalDistance": "100", "totalTime": "60"}
                            }
                            """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TmapRouteSummary> first = executor.submit(() -> client.findRoute(testSearch()));
            assertThat(upstreamStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<TmapRouteSummary> second = executor.submit(() -> client.findRoute(testSearch()));
            releaseUpstream.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).distanceMeters()).isEqualTo(100L);
            assertThat(second.get(5, TimeUnit.SECONDS).distanceMeters()).isEqualTo(100L);
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
        server.verify();
    }

    /** TTL이 지난 동일 경로는 캐시를 폐기하고 새 외부 요청으로 갱신하는지 확인합니다. */
    @Test
    void refreshesCachedSummaryAfterTtlExpires() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-11-01T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = new TmapRouteClient(
                builder.build(),
                new TmapDailyCallLimiter(2, clock),
                clock,
                Duration.ofMinutes(10),
                16);
        for (String distance : List.of("100", "200")) {
            server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                    .andRespond(withSuccess("""
                            {
                              "type": "FeatureCollection",
                              "properties": {"totalDistance": "%s", "totalTime": "60"}
                            }
                            """.formatted(distance), MediaType.APPLICATION_JSON));
        }

        assertThat(client.findRoute(testSearch()).distanceMeters()).isEqualTo(100L);
        clock.advance(Duration.ofMinutes(11));
        assertThat(client.findRoute(testSearch()).distanceMeters()).isEqualTo(200L);
        server.verify();
    }

    /**
     * 거리나 시간이 0인 응답을 실제 경로로 받아들이지 않는지 확인합니다.
     */
    @Test
    void rejectsInvalidRouteSummary() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 90);
        server.expect(requestTo(BASE_URL + "/tmap/routes/routeSequential30?version=1"))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "properties": {"totalDistance": "0", "totalTime": "0"}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRoute(testSearch()))
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.INVALID_RESPONSE));
        server.verify();
    }

    /**
     * 경유지가 없는 편도는 공식 자동차 경로안내 API와 간소화 응답을 사용하는지 확인합니다.
     */
    @Test
    void sendsSingleRouteRequestWhenWaypointListIsEmpty() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("appKey", TEST_APP_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapRouteClient client = createClient(builder, 90);
        TmapRouteSearch search = new TmapRouteSearch(
                new TmapCoordinate(new BigDecimal("0.100000"), new BigDecimal("0.200000")),
                new TmapCoordinate(new BigDecimal("0.300000"), new BigDecimal("0.400000")),
                List.of());

        server.expect(requestTo(BASE_URL + "/tmap/routes?version=1&totalValue=2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("appKey", TEST_APP_KEY))
                .andExpect(content().json("""
                        {
                          "reqCoordType": "WGS84GEO",
                          "resCoordType": "WGS84GEO",
                          "startX": "0.200000",
                          "startY": "0.100000",
                          "endX": "0.400000",
                          "endY": "0.300000",
                          "searchOption": 0,
                          "carType": 1
                        }
                        """, JsonCompareMode.LENIENT))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "features": [
                            {
                              "type": "Feature",
                              "properties": {
                                "totalDistance": 4200,
                                "totalTime": 901
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmapRouteSummary result = client.findRoute(search);

        assertThat(result.distanceMeters()).isEqualTo(4200L);
        assertThat(result.durationSeconds()).isEqualTo(901L);
        assertThat(result.repeatedSegmentPercent()).isNull();
        server.verify();
    }

    private TmapRouteClient createClient(RestClient.Builder builder, int dailyLimit) {
        return new TmapRouteClient(
                builder.build(),
                new TmapDailyCallLimiter(dailyLimit, FIXED_CLOCK),
                FIXED_CLOCK);
    }

    private TmapRouteSearch testSearch() {
        return new TmapRouteSearch(
                new TmapCoordinate(new BigDecimal("0.100000"), new BigDecimal("0.200000")),
                new TmapCoordinate(new BigDecimal("0.300000"), new BigDecimal("0.400000")),
                List.of(new TmapWaypoint(
                        "7",
                        "TEST_WAYPOINT_ONLY",
                        new TmapCoordinate(
                                new BigDecimal("0.500000"),
                                new BigDecimal("0.600000")))));
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone;

        private MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(currentInstant, requestedZone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
