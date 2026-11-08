package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 공공데이터 호출 없이 공원 응답 정규화와 거리 필터를 검증합니다. */
class ParkOpenDataSourceTest {

    private static final String BASE_URL = "https://park.test.invalid/openapi";

    @Test
    void loadsParksAndKeepsNearbyValidCoordinates() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = properties();
        ParkOpenDataSource source = new ParkOpenDataSource(
                builder.build(),
                properties,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));

        server.expect(requestTo(BASE_URL
                        + "/tn_pubr_public_cty_park_info_api"
                        + "?serviceKey=TEST_KEY_ONLY&type=json&pageNo=1&numOfRows=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "header": {"resultCode": "00"},
                          "body": {
                            "items": [
                              {
                                "manageNo": "PARK-1",
                                "parkNm": "NEAR_PARK_TEST_ONLY",
                                "parkSe": "근린공원",
                                "latitude": "37.566500",
                                "longitude": "126.978000"
                              },
                              {
                                "manageNo": "PARK-2",
                                "parkNm": "INVALID_PARK_TEST_ONLY",
                                "latitude": "999",
                                "longitude": "126.978000"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.PARK,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                10_000,
                20);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("NEAR_PARK_TEST_ONLY");
            assertThat(candidate.theme()).isEqualTo(ScenicTheme.PARK);
            assertThat(candidate.source()).isEqualTo("PUBLIC_PARK");
            assertThat(candidate.sourceCategory()).isEqualTo("근린공원");
            assertThat(candidate.id()).isNegative();
        });
        server.verify();
    }

    @Test
    void acceptsSingleObjectItemsUsedByTheActualParkApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = properties();
        ParkOpenDataSource source = new ParkOpenDataSource(
                builder.build(),
                properties,
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));

        server.expect(requestTo(BASE_URL
                        + "/tn_pubr_public_cty_park_info_api"
                        + "?serviceKey=TEST_KEY_ONLY&type=json&pageNo=1&numOfRows=100"))
                .andRespond(withSuccess("""
                        {
                          "header": {"resultCode": "00"},
                          "body": {
                            "items": {
                              "manageNo": "PARK-SINGLE",
                              "parkNm": "SINGLE_PARK_TEST_ONLY",
                              "latitude": "37.566500",
                              "longitude": "126.978000"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(source.findNearby(
                ScenicTheme.PARK,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                10_000,
                20))
                .singleElement()
                .extracting(candidate -> candidate.name())
                .isEqualTo("SINGLE_PARK_TEST_ONLY");
        server.verify();
    }

    private PublicDataProperties properties() {
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setParkPageSize(100);
        properties.setParkMaxPages(2);
        return properties;
    }
}
