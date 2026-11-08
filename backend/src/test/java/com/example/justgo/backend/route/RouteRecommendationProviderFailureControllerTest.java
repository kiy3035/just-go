package com.example.justgo.backend.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.justgo.backend.common.ApiExceptionHandler;
import com.example.justgo.backend.common.TraceIdFilter;
import com.example.justgo.backend.integration.routing.DrivingRouteProvider;
import com.example.justgo.backend.integration.routing.tmap.TmapClientErrorType;
import com.example.justgo.backend.integration.routing.tmap.TmapRouteClientException;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 외부 경로 제공자 인증 장애가 Android에 안전한 HTTP 계약으로 전달되는지 검증합니다. */
@WebMvcTest(RouteRecommendationController.class)
@Import({
        RouteRecommendationService.class,
        CandidatePlaceSelectionStore.class,
        ApiExceptionHandler.class,
        TraceIdFilter.class
})
class RouteRecommendationProviderFailureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DrivingRouteProvider drivingRouteProvider;

    @MockitoBean
    private SpotCandidateQuery spotCandidateQuery;

    /** DB 후보 조회와 외부 제공자 인증 실패를 비밀값 없는 503 응답으로 준비합니다. */
    @BeforeEach
    void setUpProviderFailure() {
        when(spotCandidateQuery.loadNearbyVerifiedCandidates(
                any(ScenicTheme.class),
                any(java.math.BigDecimal.class),
                any(java.math.BigDecimal.class),
                anyLong(),
                anyInt()))
                .thenReturn(List.of());
        when(drivingRouteProvider.calculate(any()))
                .thenThrow(new TmapRouteClientException(
                        TmapClientErrorType.AUTHENTICATION,
                        "TEST_SECRET_MUST_NOT_BE_EXPOSED"));
    }

    /** 인증 오류에도 traceId를 유지하고 키·원시 예외 없이 공통 오류만 반환합니다. */
    @Test
    void returnsSafeServiceUnavailableResponse() throws Exception {
        mockMvc.perform(post("/api/v1/routes/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(TraceIdFilter.TRACE_ID_HEADER, "android-tmap-failure-1")
                        .content("""
                                {
                                  "latitude": 0,
                                  "longitude": 0,
                                  "desiredMinutes": 60,
                                  "tripType": "ROUND_TRIP",
                                  "theme": "RIVER"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(
                        TraceIdFilter.TRACE_ID_HEADER,
                        "android-tmap-failure-1"))
                .andExpect(jsonPath("$.code").value("ROUTE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("외부 자동차 경로 제공자 호출에 실패했습니다."))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("TEST_SECRET"))))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
}
