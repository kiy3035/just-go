package com.example.justgo.backend.integration.routing.tmap;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import com.example.justgo.backend.integration.place.TmapPoiPlaceSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * `tmap` 프로필에서만 비밀키와 timeout이 적용된 TMAP 클라이언트를 구성합니다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("tmap")
@EnableConfigurationProperties(TmapClientProperties.class)
public class TmapClientConfiguration {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * API 키를 헤더 기본값으로만 보관하고 로그나 요청 본문에 섞지 않습니다.
     */
    @Bean
    TmapRouteClient tmapRouteClient(
            RestClient.Builder restClientBuilder,
            TmapClientProperties properties,
            TmapCallUsageMapper callUsageMapper) {
        validateTimeout(properties.getConnectTimeout(), "connect timeout");
        validateTimeout(properties.getReadTimeout(), "read timeout");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        Clock clock = Clock.system(KOREA_ZONE);
        RestClient restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("appKey", properties.getAppKey())
                .build();
        return new TmapRouteClient(
                restClient,
                new PersistentTmapDailyCallLimiter(
                        properties.getDailyCallLimit(), clock, callUsageMapper),
                clock);
    }

    /** 공식 주변 카테고리를 장소 후보 원천으로 제공하되 경로 호출량과는 분리합니다. */
    @Bean
    TmapPoiPlaceSource tmapPoiPlaceSource(
            RestClient.Builder restClientBuilder,
            TmapClientProperties properties) {
        validateTimeout(properties.getConnectTimeout(), "connect timeout");
        validateTimeout(properties.getReadTimeout(), "read timeout");
        if (properties.getPoiCacheTtl() == null
                || properties.getPoiCacheTtl().isZero()
                || properties.getPoiCacheTtl().isNegative()
                || properties.getPoiCacheTtl().compareTo(Duration.ofHours(23)) > 0) {
            throw new IllegalArgumentException("TMAP POI 캐시 TTL은 0초 초과 23시간 이하여야 합니다.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("appKey", properties.getAppKey())
                .build();
        return new TmapPoiPlaceSource(restClient, properties, Clock.systemUTC());
    }

    /**
     * 0이나 과도한 timeout으로 외부 호출이 무기한 대기하지 않도록 제한합니다.
     */
    private void validateTimeout(Duration timeout, String fieldName) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "TMAP " + fieldName + "은 0초 초과 30초 이하여야 합니다.");
        }
    }
}
