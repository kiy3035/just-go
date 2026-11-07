package com.example.justgo.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 공개 환경에서 추천 API 접근 토큰이 필수로 검증되는지 확인합니다. */
class RouteApiSecurityConfigurationTest {

    private final RouteApiSecurityConfiguration configuration =
            new RouteApiSecurityConfiguration();

    /** 필수 보호가 켜진 상태에서 짧은 토큰으로 서버가 열리지 않게 합니다. */
    @Test
    void rejectsWeakRequiredAccessToken() {
        RouteApiSecurityProperties properties = new RouteApiSecurityProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("too-short");

        assertThatThrownBy(() -> configuration.routeApiRequestGuardFilter(
                properties,
                new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32~128자")
                .hasMessageNotContaining("too-short");
    }

    /** 예시 파일을 그대로 사용해 보호가 켜지는 실수를 시작 단계에서 차단합니다. */
    @Test
    void rejectsExampleAccessToken() {
        RouteApiSecurityProperties properties = new RouteApiSecurityProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("replace-with-random-client-access-token");

        assertThatThrownBy(() -> configuration.routeApiRequestGuardFilter(
                properties,
                new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32~128자")
                .hasMessageNotContaining("replace-with");
    }

    /** 충분한 형식의 토큰이면 공개 환경 보호 필터를 만들 수 있습니다. */
    @Test
    void acceptsStrongRequiredAccessToken() {
        RouteApiSecurityProperties properties = new RouteApiSecurityProperties();
        properties.setAccessTokenRequired(true);
        properties.setAccessToken("TEST_CLIENT_TOKEN_1234567890_ABCDE");

        assertThat(configuration.routeApiRequestGuardFilter(properties, new ObjectMapper()))
                .isNotNull();
    }
}
