package com.example.justgo.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 웹 입력 시안에서 합의한 핵심 선택지가 빠지지 않도록 정적 화면을 검증한다. */
class WebPrototypeTest {

    /** 왕복·편도, 시간, 다섯 풍경 테마와 미연동 안내 문구를 함께 확인한다. */
    @Test
    void containsDriveConditionControlsAndPrototypeNotice() throws IOException {
        try (var input = getClass().getResourceAsStream("/static/index.html")) {
            assertThat(input).isNotNull();
            String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(html)
                    .contains("왕복", "편도")
                    .contains("30분", "60분", "120분", "직접 입력")
                    .contains("도시", "산", "바다", "호수", "강")
                    .contains("실제 TMAP 경로 추천은 아직 연결하지 않았습니다.");
        }
    }
}
