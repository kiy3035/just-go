package com.example.justgo.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Android 네트워크 클라이언트와 백엔드 REST 계약의 핵심 경로를 검증합니다. */
public class RouteRecommendationClientTest {

    private MockWebServer server;
    private RouteRecommendationClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new RouteRecommendationClient(server.url("/").toString());
    }

    private void enqueueSuccessfulCandidateAndRoute() {
        server.enqueue(jsonResponse("""
                    {
                      "theme": "PARK",
                      "count": 1,
                      "tokenExpiresInMinutes": 30,
                      "partial": false,
                      "unavailableSources": [],
                      "candidates": [{
                        "candidateToken": "11111111-1111-1111-1111-111111111111",
                        "name": "계약 테스트 공원",
                        "theme": "PARK",
                        "latitude": 37.5,
                        "longitude": 127.0,
                        "straightLineDistanceMeters": 1234,
                        "source": "PUBLIC_PARK",
                        "sourceCategory": "도시공원"
                      }]
                    }
                    """));
        server.enqueue(jsonResponse("""
                    {
                      "mode": "LIVE",
                      "mock": false,
                      "routes": [{
                        "routeId": "contract-route",
                        "title": "계약 테스트 경로",
                        "estimatedMinutes": 61,
                        "distanceMeters": 21000,
                        "theme": "PARK",
                        "repeatedSegmentPercent": null,
                        "stops": [{
                          "sequence": 1,
                          "type": "DESTINATION",
                          "name": "계약 테스트 공원",
                          "latitude": 37.5,
                          "longitude": 127.0
                        }],
                        "reason": "계약 테스트"
                      }],
                      "alternativeRoutes": [],
                      "suggestions": [],
                      "notice": "계약 테스트"
                    }
                    """));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    /** 목록과 상세에 쓰는 시간과 행사 데이터를 보존하며 별도 장소 선택 토큰 없이 요청한다. */
    @Test
    public void readsRankedRouteLegAndEventWithoutCandidateToken() throws Exception {
        server.enqueue(jsonResponse("""
                {"mode":"LIVE","mock":false,"routes":[{
                  "routeId":"city-event-test","title":"TEST_VENUE","theme":"CITY",
                  "estimatedMinutes":60,"durationSeconds":3590,"distanceMeters":25000,
                  "stops":[{"sequence":1,"type":"DESTINATION","name":"TEST_VENUE","latitude":1,"longitude":2}],
                  "legSeconds":[3590],
                  "event":{"name":"TEST_EVENT","venue":"TEST_VENUE",
                    "opensAt":"2030-01-01T18:00:00+09:00","closesAt":"2030-01-01T19:00:00+09:00",
                    "arrivesAt":"2030-01-01T18:30:00+09:00"}
                }]}
                """));

        var result = client.recommend(1, 2, 60, "ONE_WAY", "CITY");
        var route = result.routes().get(0);
        assertEquals(3590L, route.durationSeconds());
        assertEquals(java.util.List.of(3590L), route.legSeconds());
        assertEquals("TEST_EVENT", route.event().name());
        assertEquals(30, route.event().arrivesAt().getMinute());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertFalse(new JSONObject(request.getBody().readUtf8()).has("candidateToken"));
    }

    @Test
    public void candidateTokenConnectsCandidateAndRecommendationEndpoints() throws Exception {
        enqueueSuccessfulCandidateAndRoute();
        RouteRecommendationClient.CandidateList candidates = client.findCandidates(
                37.5, 127.0, 60, "ONE_WAY", "PARK", 50);

        assertFalse(candidates.partial());
        assertEquals(1, candidates.candidates().size());
        RecordedRequest candidateRequest = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(candidateRequest);
        assertEquals("GET", candidateRequest.getMethod());
        assertEquals(RouteRecommendationClient.CANDIDATE_PATH,
                candidateRequest.getRequestUrl().encodedPath());
        assertEquals("60", candidateRequest.getRequestUrl().queryParameter("desiredMinutes"));
        assertEquals("ONE_WAY", candidateRequest.getRequestUrl().queryParameter("tripType"));
        assertEquals("PARK", candidateRequest.getRequestUrl().queryParameter("theme"));
        assertEquals("50", candidateRequest.getRequestUrl().queryParameter("limit"));

        String token = candidates.candidates().get(0).token();
        RouteRecommendationClient.Recommendation recommendation = client.recommend(
                37.5, 127.0, 60, "ONE_WAY", "PARK", token);

        RecordedRequest routeRequest = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(routeRequest);
        assertEquals("POST", routeRequest.getMethod());
        assertEquals(RouteRecommendationClient.RECOMMENDATION_PATH,
                routeRequest.getRequestUrl().encodedPath());
        JSONObject request = new JSONObject(routeRequest.getBody().readUtf8());
        assertEquals(token, request.getString("candidateToken"));
        assertEquals("ONE_WAY", request.getString("tripType"));
        assertEquals("PARK", request.getString("theme"));
        assertEquals(RouteRecommendationClient.Mode.LIVE, recommendation.mode());
        assertEquals("contract-route", recommendation.routes().get(0).routeId());
        assertEquals("계약 테스트", recommendation.notice());
        assertEquals("계약 테스트", recommendation.routes().get(0).reason());
        assertNull(recommendation.routes().get(0).repeatedSegmentPercent());
        assertEquals(RouteRecommendationClient.StopType.DESTINATION,
                recommendation.routes().get(0).stops().get(0).type());

        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""
                        {
                          "code": "INVALID_CANDIDATE_TOKEN",
                          "message": "장소 후보가 만료됐습니다.",
                          "fieldErrors": {"candidateToken": "만료"}
                        }
                        """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK", token);
            fail("구조화된 HTTP 오류가 필요합니다.");
        } catch (RouteRecommendationClient.HttpError error) {
            assertEquals(400, error.status);
            assertEquals("INVALID_CANDIDATE_TOKEN", error.code);
            assertEquals("장소 후보가 만료됐습니다.", error.getMessage());
        }
    }

    @Test
    public void acceptsRiverRouteForWaterRequest() throws Exception {
        server.enqueue(jsonResponse("""
                {"mode":"LIVE","mock":false,"routes":[{
                  "routeId":"water-route","title":"강변 경로","theme":"RIVER",
                  "estimatedMinutes":60,"durationSeconds":3600,"distanceMeters":25000,
                  "stops":[{"sequence":1,"type":"DESTINATION","name":"강변","latitude":37.5,"longitude":127.0}],
                  "legSeconds":[3600]
                }]}
                """));

        var result = client.recommend(37.5, 127.0, 60, "ONE_WAY", "WATER");

        assertEquals("RIVER", result.routes().get(0).theme());
    }

    @Test
    public void rejectsCandidateOutsideRequestedTheme() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "theme":"PARK","count":1,"tokenExpiresInMinutes":30,
                  "partial":false,"unavailableSources":[],
                  "candidates":[{
                    "candidateToken":"11111111-1111-1111-1111-111111111111",
                    "name":"잘못 섞인 도시 후보","theme":"CITY",
                    "latitude":37.5,"longitude":127.0,"straightLineDistanceMeters":1000,
                    "source":"TMAP_POI","sourceCategory":"도시 명소"
                  }]
                }
                """));

        try {
            client.findCandidates(37.5, 127.0, 60, "ONE_WAY", "PARK", 50);
            fail("요청 테마 밖의 후보를 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void rejectsRouteThemeAndTimeThatDoNotMatchRequestContract() throws Exception {
        server.enqueue(jsonResponse("""
                {"mode":"LIVE","mock":false,"routes":[{
                  "routeId":"wrong-theme","title":"잘못된 테마","theme":"CITY",
                  "estimatedMinutes":60,"durationSeconds":3600,"distanceMeters":25000,
                  "stops":[{"sequence":1,"type":"DESTINATION","name":"도착지","latitude":37.5,"longitude":127.0}]
                }]}
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK");
            fail("요청 테마와 다른 일반 추천 경로를 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {"mode":"LIVE","mock":false,"routes":[{
                  "routeId":"wrong-duration","title":"잘못된 시간","theme":"PARK",
                  "estimatedMinutes":59,"durationSeconds":3600,"distanceMeters":25000,
                  "stops":[{"sequence":1,"type":"DESTINATION","name":"도착지","latitude":37.5,"longitude":127.0}]
                }]}
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK");
            fail("초 단위 시간과 분 단위 표시가 다른 경로를 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {"mode":"LIVE","mock":false,"routes":[{
                  "routeId":"wrong-legs","title":"잘못된 구간 시간","theme":"PARK",
                  "estimatedMinutes":60,"durationSeconds":3600,"distanceMeters":25000,
                  "stops":[{"sequence":1,"type":"DESTINATION","name":"도착지","latitude":37.5,"longitude":127.0}],
                  "legSeconds":[3599]
                }]}
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK");
            fail("구간 시간 합계와 전체 시간이 다른 경로를 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void disconnectsAnActiveRequestWhenScreenStops() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RouteRecommendationClient.Recommendation> pending = executor.submit(() ->
                    client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK", null));
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));

            client.cancelActiveRequest();

            try {
                pending.get(5, TimeUnit.SECONDS);
                fail("취소된 요청은 성공하면 안 됩니다.");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
        } finally {
            client.cancelActiveRequest();
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectsCandidateResponseThatBreaksBackendContract() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "theme": "PARK",
                  "count": 1,
                  "tokenExpiresInMinutes": 30,
                  "partial": false,
                  "unavailableSources": [],
                  "candidates": [{
                    "candidateToken": "not-a-uuid",
                    "name": "손상 응답 테스트",
                    "theme": "PARK",
                    "latitude": 37.5,
                    "longitude": 127.0,
                    "straightLineDistanceMeters": 1,
                    "source": "PUBLIC_PARK",
                    "sourceCategory": "도시공원"
                  }]
                }
                """));

        try {
            client.findCandidates(37.5, 127.0, 60, "ONE_WAY", "PARK", 50);
            fail("백엔드 계약을 벗어난 후보 응답은 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {
                  "theme": "PARK",
                  "count": 1,
                  "tokenExpiresInMinutes": 30,
                  "partial": false,
                  "unavailableSources": [],
                  "candidates": [{
                    "candidateToken": "11111111-1111-1111-1111-111111111111",
                    "name": "%s",
                    "theme": "PARK",
                    "latitude": 37.5,
                    "longitude": 127.0,
                    "straightLineDistanceMeters": 1,
                    "source": "PUBLIC_PARK",
                    "sourceCategory": "도시공원"
                  }]
                }
                """.formatted("N".repeat(101))));
        try {
            client.findCandidates(37.5, 127.0, 60, "ONE_WAY", "PARK", 50);
            fail("표시 길이 계약을 벗어난 후보 응답은 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {
                  "theme": "CITY",
                  "count": 0,
                  "tokenExpiresInMinutes": 30,
                  "partial": false,
                  "unavailableSources": [],
                  "candidates": []
                }
                """));
        try {
            client.findCandidates(37.5, 127.0, 60, "ONE_WAY", "PARK", 50);
            fail("요청과 다른 테마의 후보 응답은 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void rejectsRouteResponseWithInvalidNumbersAndStopOrder() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "mode": "LIVE",
                  "mock": false,
                  "routes": [{
                    "routeId": "invalid-route",
                    "title": "손상 경로 테스트",
                    "estimatedMinutes": -1,
                    "distanceMeters": 100,
                    "theme": "PARK",
                    "repeatedSegmentPercent": null,
                    "stops": [
                      {"sequence": 1, "type": "WAYPOINT", "name": "첫 지점", "latitude": 37.5, "longitude": 127.0},
                      {"sequence": 2, "type": "DESTINATION", "name": "도착지", "latitude": 37.6, "longitude": 127.1}
                    ],
                    "reason": "손상 응답"
                  }],
                  "alternativeRoutes": [],
                  "notice": ""
                }
                """));

        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK", null);
            fail("백엔드 계약을 벗어난 경로 응답은 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {
                  "mode": "LIVE",
                  "mock": false,
                  "routes": [{
                    "routeId": "invalid-stop-order",
                    "title": "중복 순서 테스트",
                    "estimatedMinutes": 60,
                    "distanceMeters": 100,
                    "theme": "PARK",
                    "repeatedSegmentPercent": null,
                    "stops": [
                      {"sequence": 1, "type": "WAYPOINT", "name": "첫 지점", "latitude": 37.5, "longitude": 127.0},
                      {"sequence": 1, "type": "DESTINATION", "name": "도착지", "latitude": 37.6, "longitude": 127.1}
                    ],
                    "reason": "손상 응답"
                  }],
                  "alternativeRoutes": [],
                  "notice": ""
                }
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK", null);
            fail("중복된 지점 순번은 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void rejectsLiveRouteThatCannotBeSentToNavigation() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "mode": "LIVE",
                  "mock": false,
                  "routes": [{
                    "routeId": "missing-destination",
                    "title": "목적지 누락 테스트",
                    "estimatedMinutes": 60,
                    "distanceMeters": 100,
                    "theme": "PARK",
                    "repeatedSegmentPercent": null,
                    "stops": [
                      {"sequence": 1, "type": "WAYPOINT", "name": "경유지만 있음", "latitude": 37.5, "longitude": 127.0}
                    ],
                    "reason": "손상 응답"
                  }],
                  "alternativeRoutes": [],
                  "notice": ""
                }
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ROUND_TRIP", "PARK", null);
            fail("목적지가 없는 LIVE 경로는 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }

        server.enqueue(jsonResponse("""
                {
                  "mode": "LIVE",
                  "mock": false,
                  "routes": [{
                    "routeId": "destination-before-waypoint",
                    "title": "목적지 순서 테스트",
                    "estimatedMinutes": 60,
                    "distanceMeters": 100,
                    "theme": "PARK",
                    "repeatedSegmentPercent": null,
                    "stops": [
                      {"sequence": 1, "type": "DESTINATION", "name": "잘못된 목적지", "latitude": 37.5, "longitude": 127.0},
                      {"sequence": 2, "type": "WAYPOINT", "name": "뒤늦은 경유지", "latitude": 37.6, "longitude": 127.1}
                    ],
                    "reason": "손상 응답"
                  }],
                  "alternativeRoutes": [],
                  "notice": ""
                }
                """));
        try {
            client.recommend(37.5, 127.0, 60, "ONE_WAY", "PARK", null);
            fail("마지막이 아닌 목적지는 거부해야 합니다.");
        } catch (RouteRecommendationClient.InvalidResponse expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void distinguishesUnavailableCandidateSourcesFromNormalEmptyResult() throws Exception {
        server.enqueue(jsonResponse("""
                {
                  "theme": "SEA",
                  "count": 0,
                  "tokenExpiresInMinutes": 30,
                  "partial": true,
                  "unavailableSources": ["TOUR_API"],
                  "candidates": []
                }
                """));
        server.enqueue(jsonResponse("""
                {
                  "theme": "SEA",
                  "count": 0,
                  "tokenExpiresInMinutes": 30,
                  "partial": false,
                  "unavailableSources": [],
                  "candidates": []
                }
                """));

        RouteRecommendationClient.CandidateList unavailable = client.findCandidates(
                37.5, 127.0, 60, "ONE_WAY", "SEA", 50);
        RouteRecommendationClient.CandidateList empty = client.findCandidates(
                37.5, 127.0, 60, "ONE_WAY", "SEA", 50);

        assertTrue(unavailable.sourceUnavailable());
        assertFalse(empty.sourceUnavailable());
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }
}
