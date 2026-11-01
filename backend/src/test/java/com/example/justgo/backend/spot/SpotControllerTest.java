package com.example.justgo.backend.spot;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.example.justgo.backend.common.ApiExceptionHandler;
import com.example.justgo.backend.common.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 장소 후보 조회의 쿼리 파라미터와 JSON 계약을 검증합니다.
 */
@WebMvcTest(SpotController.class)
@Import({ApiExceptionHandler.class, TraceIdFilter.class})
class SpotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpotService spotService;

    /**
     * 테마와 제한값이 서비스에 전달되고 검수 후보 응답이 반환되는지 확인합니다.
     */
    @Test
    void returnsVerifiedCandidates() throws Exception {
        SpotCandidateResponse spot = new SpotCandidateResponse(
                1L,
                "VERIFIED_SPOT_TEST_ONLY",
                ScenicTheme.LAKE,
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000"));
        when(spotService.findVerifiedCandidates(ScenicTheme.LAKE, 5))
                .thenReturn(new SpotCandidateListResponse(ScenicTheme.LAKE, 1, List.of(spot)));

        mockMvc.perform(get("/api/v1/spots")
                        .queryParam("theme", "LAKE")
                        .queryParam("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("LAKE"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.spots[0].name").value("VERIFIED_SPOT_TEST_ONLY"));
    }

    /**
     * 알 수 없는 테마 문자열을 공통 오류 계약으로 반환하는지 확인합니다.
     */
    @Test
    void rejectsUnknownTheme() throws Exception {
        mockMvc.perform(get("/api/v1/spots").queryParam("theme", "FOREST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors.theme").exists());
    }
}
