package com.example.justgo.backend.route;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 추천 정책 설정을 모든 실행 프로필에서 활성화합니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RouteRecommendationProperties.class)
public class RouteRecommendationConfiguration {
}
