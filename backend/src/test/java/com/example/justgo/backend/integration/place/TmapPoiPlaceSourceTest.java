package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.justgo.backend.integration.routing.tmap.TmapClientProperties;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** TMAP 공식 주변 카테고리 요청과 POI 정규화를 외부 호출 없이 검증합니다. */
class TmapPoiPlaceSourceTest {

    @Test
    void mapsOfficialCityCategoriesAndPrefersEntranceCoordinates() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://tmap.test.invalid");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapClientProperties properties = new TmapClientProperties();
        properties.setAppKey("TEST_KEY_ONLY");
        properties.setPoiRadiusKm(33);
        properties.setPoiPageSize(100);
        TmapPoiPlaceSource source = new TmapPoiPlaceSource(
                builder.build(),
                properties,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));

        server.expect(requestTo(containsString("/tmap/pois/search/around?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "searchPoiInfo": {
                            "totalCount": "1",
                            "pois": {
                              "poi": [{
                                "id": "CITY-POI-1",
                                "name": "문화시설 테스트",
                                "frontLat": "37.566700",
                                "frontLon": "126.978200",
                                "noorLat": "37.600000",
                                "noorLon": "127.000000"
                              }]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.CITY,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                50_000,
                20);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.theme()).isEqualTo(ScenicTheme.CITY);
            assertThat(candidate.source()).isEqualTo("TMAP_POI");
            assertThat(candidate.sourceCategory()).isEqualTo("주요시설물;문화시설;공연장");
            assertThat(candidate.latitude()).isEqualByComparingTo("37.566700");
            assertThat(candidate.longitude()).isEqualByComparingTo("126.978200");
        });
        server.verify();
    }

    @Test
    void sharesOnePoiCallForConcurrentIdenticalSearches() throws Exception {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://tmap.test.invalid");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TmapClientProperties properties = new TmapClientProperties();
        properties.setAppKey("TEST_KEY_ONLY");
        CountDownLatch upstreamStarted = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        TmapPoiPlaceSource source = new TmapPoiPlaceSource(
                builder.build(),
                properties,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));

        server.expect(requestTo(containsString("/tmap/pois/search/around?")))
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
                              "searchPoiInfo": {"pois": {"poi": [{
                                "id": "CONCURRENT-POI-1",
                                "name": "동시 POI 테스트",
                                "frontLat": "37.566700",
                                "frontLon": "126.978200"
                              }]}}
                            }
                            """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<SpotCandidateRecord>> first = executor.submit(() -> source.findNearby(
                    ScenicTheme.CITY,
                    new BigDecimal("37.566500"),
                    new BigDecimal("126.978000"),
                    20_000,
                    20));
            assertThat(upstreamStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<SpotCandidateRecord>> second = executor.submit(() -> source.findNearby(
                    ScenicTheme.CITY,
                    new BigDecimal("37.566500"),
                    new BigDecimal("126.978000"),
                    20_000,
                    20));
            releaseUpstream.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).hasSize(1);
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
        server.verify();
    }
}
