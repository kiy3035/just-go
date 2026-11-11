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

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }
}
