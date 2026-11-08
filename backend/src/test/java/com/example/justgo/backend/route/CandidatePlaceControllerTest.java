package com.example.justgo.backend.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.justgo.backend.common.ApiExceptionHandler;
import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 넓은 장소 후보 조회의 쿼리 검증과 JSON 계약을 확인합니다. */
@WebMvcTest(CandidatePlaceController.class)
@Import(ApiExceptionHandler.class)
class CandidatePlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidatePlaceService candidatePlaceService;

    @Test
    void returnsCandidateListContract() throws Exception {
        when(candidatePlaceService.findCandidates(
                any(), any(), anyInt(), any(), any(), anyInt()))
                .thenReturn(new CandidatePlaceListResponse(
                        ScenicTheme.PARK,
                        0,
                        30,
                        List.of()));

        mockMvc.perform(get("/api/v1/places/candidates")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("desiredMinutes", "60")
                        .param("tripType", "ONE_WAY")
                        .param("theme", "PARK")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("PARK"))
                .andExpect(jsonPath("$.tokenExpiresInMinutes").value(30))
                .andExpect(jsonPath("$.candidates").isArray());
    }

    @Test
    void rejectsOutOfRangeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/places/candidates")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("desiredMinutes", "60")
                        .param("tripType", "ONE_WAY")
                        .param("theme", "PARK")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
