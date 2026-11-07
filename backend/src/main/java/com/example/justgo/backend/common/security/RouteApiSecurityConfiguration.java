package com.example.justgo.backend.common.security;

import java.time.Clock;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 추천 API 보호 설정을 검증하고 요청 필터를 구성합니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RouteApiSecurityProperties.class)
public class RouteApiSecurityConfiguration {

    private static final Pattern SAFE_ACCESS_TOKEN = Pattern.compile("[A-Za-z0-9_-]{32,128}");
    private static final String EXAMPLE_ACCESS_TOKEN = "replace-with-random-client-access-token";

    /**
     * 보호가 필요한 환경에서 약하거나 비어 있는 토큰으로 서버가 열리지 않게 시작을 중단합니다.
     */
    @Bean
    RouteApiRequestGuardFilter routeApiRequestGuardFilter(
            RouteApiSecurityProperties properties,
            ObjectMapper objectMapper) {
        validateAccessToken(properties);
        return new RouteApiRequestGuardFilter(properties, objectMapper, Clock.systemUTC());
    }

    /** 실제 비밀값을 출력하지 않고 토큰 길이와 허용 문자만 검증합니다. */
    private void validateAccessToken(RouteApiSecurityProperties properties) {
        if (!properties.isAccessTokenRequired()) {
            return;
        }
        String accessToken = properties.getAccessToken();
        if (accessToken == null
                || EXAMPLE_ACCESS_TOKEN.equals(accessToken)
                || !SAFE_ACCESS_TOKEN.matcher(accessToken).matches()) {
            throw new IllegalStateException(
                    "추천 API 접근 토큰은 영문·숫자·밑줄·하이픈 32~128자로 설정해야 합니다.");
        }
    }
}
