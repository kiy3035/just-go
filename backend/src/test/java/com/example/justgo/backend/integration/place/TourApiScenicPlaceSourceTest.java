package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateRecord;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** TourAPI의 자연·도시 후보 분류를 외부 호출 없이 검증합니다. */
class TourApiScenicPlaceSourceTest {

    private static final String BASE_URL = "https://tour.test.invalid/KorService2";

    @Test
    void mapsOnlyRequestedThemeFromNearbyTourismItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setTourPageSize(200);
        properties.setTourSearchMaxCenters(1);
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(BASE_URL
                        + "/locationBasedList2?serviceKey=TEST_KEY_ONLY"
                        + "&MobileOS=ETC&MobileApp=just-go&_type=json&arrange=E"
                        + "&mapX=126.978000&mapY=37.566500&radius=20000"
                        + "&numOfRows=200&pageNo=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {
                              "items": {
                                "item": [
                                  {
                                    "contentid": "SEA-1",
                                    "contenttypeid": "12",
                                    "title": "자연전망대 테스트",
                                    "lclsSystm2": "NA02",
                                    "lclsSystm3": "NA020800",
                                    "mapx": "126.980000",
                                    "mapy": "37.568000"
                                  },
                                  {
                                    "contentid": "CITY-1",
                                    "contenttypeid": "14",
                                    "title": "도시박물관 테스트",
                                    "lclsSystm2": "VE07",
                                    "lclsSystm3": "VE070100",
                                    "mapx": "126.981000",
                                    "mapy": "37.569000"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.SEA,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                75_000,
                20);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).contains("자연전망대");
            assertThat(candidate.theme()).isEqualTo(ScenicTheme.SEA);
            assertThat(candidate.source()).isEqualTo("TOUR_API");
            assertThat(candidate.sourceCategory()).isEqualTo("NA02/NA020800");
        });
        var cityResultFromSameCachedResponse = source.findNearby(
                ScenicTheme.CITY,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                75_000,
                20);
        assertThat(cityResultFromSameCachedResponse).singleElement()
                .extracting(candidate -> candidate.name())
                .isEqualTo("도시박물관 테스트");
        server.verify();
    }

    @Test
    void sharesOneTourApiLoadAcrossConcurrentThemeRequests() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setTourSearchMaxCenters(1);
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);
        CountDownLatch upstreamStarted = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/locationBasedList2?")))
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
                              "response": {
                                "header": {"resultCode": "0000"},
                                "body": {"items": {"item": [{
                                  "contentid": "CONCURRENT-CITY-1",
                                  "contenttypeid": "14",
                                  "title": "동시 조회 도시 후보 테스트",
                                  "lclsSystm2": "VE07",
                                  "lclsSystm3": "VE070100",
                                  "mapx": "126.980000",
                                  "mapy": "37.568000"
                                }]}}
                              }
                            }
                            """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<SpotCandidateRecord>> city = executor.submit(() ->
                    source.findNearby(
                            ScenicTheme.CITY,
                            new BigDecimal("37.566500"),
                            new BigDecimal("126.978000"),
                            20_000,
                            20));
            assertThat(upstreamStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<SpotCandidateRecord>> sea = executor.submit(() ->
                    source.findNearby(
                            ScenicTheme.SEA,
                            new BigDecimal("37.566500"),
                            new BigDecimal("126.978000"),
                            20_000,
                            20));
            releaseUpstream.countDown();

            assertThat(city.get(5, TimeUnit.SECONDS)).hasSize(1);
            assertThat(sea.get(5, TimeUnit.SECONDS)).isEmpty();
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
        server.verify();
    }

    @Test
    void expandsLongDistanceSearchWithBoundedDistinctCentersAndDeduplicatesItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setTourSearchMaxCenters(7);
        properties.setTourSearchRingStepMeters(40000);
        Set<String> requestedUris = new LinkedHashSet<>();
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(ExpectedCount.times(7), request ->
                        requestedUris.add(request.getURI().toString()))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {
                              "items": {
                                "item": [{
                                  "contentid": "WIDE-SEA-1",
                                  "contenttypeid": "12",
                                  "title": "광역 바다 후보 테스트",
                                  "lclsSystm2": "NA02",
                                  "lclsSystm3": "NA020800",
                                  "mapx": "126.500000",
                                  "mapy": "37.500000"
                                }]
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.SEA,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                75_000,
                20);

        assertThat(requestedUris).hasSize(7);
        assertThat(result).singleElement()
                .extracting(SpotCandidateRecord::name)
                .isEqualTo("광역 바다 후보 테스트");
        server.verify();
    }

    @Test
    void excludesUnclassifiedItemsInsteadOfGuessingFromTheirNames() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/locationBasedList2?")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {
                              "items": {
                                "item": [{
                                  "contentid": "LEGACY-1",
                                  "contenttypeid": "12",
                                  "title": "이름만 호수공원인 미분류 장소",
                                  "mapx": "126.980000",
                                  "mapy": "37.568000"
                                }]
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(source.findNearby(
                ScenicTheme.ANYWHERE,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                20_000,
                20)).isEmpty();
        server.verify();
    }

    @Test
    void officialClassificationPreventsNaturalKeywordFalsePositivesAndRanksCityLandmarks() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setTourPageSize(200);
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(BASE_URL
                        + "/locationBasedList2?serviceKey=TEST_KEY_ONLY"
                        + "&MobileOS=ETC&MobileApp=just-go&_type=json&arrange=E"
                        + "&mapX=126.978000&mapY=37.566500&radius=20000"
                        + "&numOfRows=200&pageNo=1"))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {
                              "items": {
                                "item": [
                                  {
                                    "contentid": "HISTORY-1",
                                    "contenttypeid": "12",
                                    "title": "한강방어 역사기념지 테스트",
                                    "lclsSystm2": "HS04",
                                    "lclsSystm3": "HS040100",
                                    "mapx": "126.980000",
                                    "mapy": "37.568000"
                                  },
                                  {
                                    "contentid": "FACILITY-1",
                                    "contenttypeid": "28",
                                    "title": "청소년센터 테스트",
                                    "lclsSystm2": "VE10",
                                    "lclsSystm3": "VE100200",
                                    "mapx": "126.981000",
                                    "mapy": "37.569000"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.CITY,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                20_000,
                20);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(candidate -> assertThat(candidate.theme())
                .isEqualTo(ScenicTheme.CITY));
        assertThat(result.get(0).selectionPriority()).isEqualTo(1);
        assertThat(result.get(1).selectionPriority()).isEqualTo(5);
        server.verify();
    }

    @Test
    void keepsBroadMountainCategoriesButRanksOfficialMountainsFirst() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/locationBasedList2?")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {"items": {"item": [
                              {
                                "contentid": "NATURAL-PARK-1",
                                "contenttypeid": "12",
                                "title": "가까운 자연공원 테스트",
                                "lclsSystm2": "NA04",
                                "lclsSystm3": "NA040100",
                                "mapx": "126.978100",
                                "mapy": "37.566600"
                              },
                              {
                                "contentid": "MOUNTAIN-1",
                                "contenttypeid": "12",
                                "title": "대표 산악 장소 테스트",
                                "lclsSystm2": "NA01",
                                "lclsSystm3": "NA010100",
                                "mapx": "126.990000",
                                "mapy": "37.580000"
                              }
                            ]}}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.MOUNTAIN,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                20_000,
                20);

        assertThat(result).extracting(SpotCandidateRecord::name)
                .containsExactly("대표 산악 장소 테스트", "가까운 자연공원 테스트");
        assertThat(result).extracting(SpotCandidateRecord::selectionPriority)
                .containsExactly(0, 3);
        server.verify();
    }

    @Test
    void ranksNaturalSeaAndWaterPlacesBeforeFishingLeisure() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/locationBasedList2?")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {"items": {"item": [
                              {
                                "contentid": "SEA-FISHING-1",
                                "contenttypeid": "28",
                                "title": "가까운 바다낚시 테스트",
                                "lclsSystm2": "LS02",
                                "lclsSystm3": "LS020600",
                                "mapx": "126.978100",
                                "mapy": "37.566600"
                              },
                              {
                                "contentid": "ISLAND-1",
                                "contenttypeid": "12",
                                "title": "자연 섬 테스트",
                                "lclsSystm2": "NA02",
                                "lclsSystm3": "NA020500",
                                "mapx": "126.990000",
                                "mapy": "37.580000"
                              },
                              {
                                "contentid": "FRESHWATER-FISHING-1",
                                "contenttypeid": "28",
                                "title": "가까운 민물낚시 테스트",
                                "lclsSystm2": "LS02",
                                "lclsSystm3": "LS020500",
                                "mapx": "126.978200",
                                "mapy": "37.566700"
                              },
                              {
                                "contentid": "LAKE-1",
                                "contenttypeid": "12",
                                "title": "자연 호수 테스트",
                                "lclsSystm2": "NA02",
                                "lclsSystm3": "NA020200",
                                "mapx": "126.991000",
                                "mapy": "37.581000"
                              }
                            ]}}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var sea = source.findNearby(
                ScenicTheme.SEA,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                20_000,
                20);
        var water = source.findNearby(
                ScenicTheme.WATER,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                20_000,
                20);

        assertThat(sea).extracting(SpotCandidateRecord::name)
                .containsExactly("자연 섬 테스트", "가까운 바다낚시 테스트");
        assertThat(water).extracting(SpotCandidateRecord::name)
                .containsExactly("자연 호수 테스트", "가까운 민물낚시 테스트");
        assertThat(sea).extracting(SpotCandidateRecord::selectionPriority)
                .containsExactly(0, 2);
        assertThat(water).extracting(SpotCandidateRecord::selectionPriority)
                .containsExactly(0, 2);
        server.verify();
    }
}
