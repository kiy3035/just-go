package com.example.justgo.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
        server.start();
        client = new RouteRecommendationClient(server.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void candidateTokenConnectsCandidateAndRecommendationEndpoints() throws Exception {
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
        assertEquals(RouteRecommendationClient.StopType.DESTINATION,
                recommendation.routes().get(0).stops().get(0).type());
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }
}
