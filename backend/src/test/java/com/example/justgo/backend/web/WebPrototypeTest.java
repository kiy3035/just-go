package com.example.justgo.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 웹 입력 시안에서 합의한 핵심 선택지가 빠지지 않도록 정적 화면을 검증합니다. */
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
}
