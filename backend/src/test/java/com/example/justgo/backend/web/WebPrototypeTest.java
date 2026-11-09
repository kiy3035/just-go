package com.example.justgo.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 웹 화면의 핵심 선택지와 백엔드 REST 연결 경로를 정적 검증합니다. */
class WebPrototypeTest {

    /** 왕복·편도, 시간, 다섯 풍경 테마와 코스 검색 버튼을 함께 확인합니다. */
    @Test
    void containsDriveConditionControlsAndSearchAction() throws IOException {
        try (var input = getClass().getResourceAsStream("/static/index.html")) {
            assertThat(input).isNotNull();
            String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(html)
                    .contains("왕복", "편도")
                    .contains("30분", "60분", "120분", "직접 입력")
                    .contains("도시", "산", "바다", "호수", "강")
                    .contains("이 조건으로 코스 찾기");
        }
    }

    @Test
    void connectsCandidateAndRecommendationEndpointsWithoutFakeTimer() throws IOException {
        try (var htmlInput = getClass().getResourceAsStream("/static/index.html");
                var scriptInput = getClass().getResourceAsStream("/static/drive.js")) {
            assertThat(htmlInput).isNotNull();
            assertThat(scriptInput).isNotNull();
            String html = new String(htmlInput.readAllBytes(), StandardCharsets.UTF_8);
            String script = new String(scriptInput.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(html).contains("id=\"result-dialog\"");
            assertThat(script)
                    .contains("navigator.geolocation")
                    .contains("/api/v1/places/candidates")
                    .contains("/api/v1/routes/recommendations")
                    .contains("candidateToken")
                    .contains("AbortController")
                    .contains("CANDIDATE_TIMEOUT_MS", "ROUTE_TIMEOUT_MS")
                    .contains("이 장소로 코스 찾기")
                    .doesNotContain("PREVIEW_DURATION_MS");
        }
    }
}
