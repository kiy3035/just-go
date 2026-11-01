package com.example.justgo.backend.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.justgo.backend.common.ApiExceptionHandler;
import com.example.justgo.backend.common.TraceIdFilter;
import com.example.justgo.backend.integration.routing.MockDrivingRouteProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP 계층의 JSON 계약, 입력 검증, 추적 헤더를 데이터베이스 없이 확인합니다.
 */
@WebMvcTest(RouteRecommendationController.class)
@ActiveProfiles("mock")
@Import({
        RouteRecommendationService.class,
        MockDrivingRouteProvider.class,
        ApiExceptionHandler.class,
        TraceIdFilter.class
})
class RouteRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 정상 요청이 명시적인 MOCK 결과와 안전한 추적 헤더를 반환하는지 확인합니다.
     */
    @Test
    void returnsMockRecommendation() throws Exception {
        mockMvc.perform(post("/api/v1/routes/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TraceIdFilter.TRACE_ID_HEADER, "web-test-1")
                        .content("""
                                {
                                  "latitude": 0,
                                  "longitude": 0,
                                  "desiredMinutes": 60,
                                  "theme": "SEA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "web-test-1"))
                .andExpect(jsonPath("$.mode").value("MOCK"))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.routes[0].routeId").value("MOCK-ROUTE-001"));
    }

    /**
     * 허용하지 않은 시간은 서비스까지 도달하더라도 400 오류 계약으로 변환되는지 확인합니다.
     */
    @Test
    void rejectsUnsupportedDuration() throws Exception {
        mockMvc.perform(post("/api/v1/routes/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 0,
                                  "longitude": 0,
                                  "desiredMinutes": 45,
                                  "theme": "CITY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DURATION"));
    }

    /**
     * 좌표 경계를 벗어나면 필드별 400 오류를 반환하는지 확인합니다.
     */
    @Test
    void rejectsOutOfRangeCoordinate() throws Exception {
        mockMvc.perform(post("/api/v1/routes/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 91,
                                  "longitude": 0,
                                  "desiredMinutes": 30,
                                  "theme": "CITY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());
    }
}
