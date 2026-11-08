package com.example.justgo.backend.integration.place;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** public-data 프로필에서만 공원·TourAPI 후보 원천을 활성화합니다. */
@Configuration(proxyBeanMethods = false)
@Profile("public-data")
@EnableConfigurationProperties(PublicDataProperties.class)
public class PublicDataConfiguration {

    @Bean
    ParkOpenDataSource parkOpenDataSource(
            RestClient.Builder builder,
            PublicDataProperties properties) {
        validate(properties);
        return new ParkOpenDataSource(
                restClient(builder, normalizeParkBaseUrl(properties.getParkBaseUrl()), properties),
                properties,
                Clock.systemUTC());
    }

    @Bean
    TourApiScenicPlaceSource tourApiScenicPlaceSource(
            RestClient.Builder builder,
            PublicDataProperties properties) {
        validate(properties);
        return new TourApiScenicPlaceSource(
                restClient(builder, normalizeTourBaseUrl(properties.getTourBaseUrl()), properties),
                properties);
    }

    private RestClient restClient(
            RestClient.Builder builder,
            String baseUrl,
            PublicDataProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private void validate(PublicDataProperties properties) {
        validateDuration(properties.getConnectTimeout(), "connect timeout", Duration.ofSeconds(30));
        validateDuration(properties.getReadTimeout(), "read timeout", Duration.ofSeconds(30));
        validateDuration(properties.getParkCacheTtl(), "공원 캐시 TTL", Duration.ofHours(23));
        validateDuration(properties.getTourCacheTtl(), "TourAPI 캐시 TTL", Duration.ofHours(23));
    }

    private void validateDuration(Duration value, String label, Duration maximum) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(label + "은 0초 초과 " + maximum + " 이하여야 합니다.");
        }
    }

    /** 사용자가 전체 엔드포인트 또는 기본 URL 어느 쪽을 넣어도 중복 경로가 생기지 않게 합니다. */
    private String normalizeParkBaseUrl(String value) {
        return stripEndpoint(value, "/tn_pubr_public_cty_park_info_api");
    }

    private String normalizeTourBaseUrl(String value) {
        return stripEndpoint(value, "/locationBasedList2");
    }

    private String stripEndpoint(String value, String endpoint) {
        String normalized = value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
        return normalized.endsWith(endpoint)
                ? normalized.substring(0, normalized.length() - endpoint.length())
                : normalized;
    }
}
