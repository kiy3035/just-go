package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** TourAPI의 자연·도시 후보 분류를 외부 호출 없이 검증합니다. */
class TourApiScenicPlaceSourceTest {

    private static final String BASE_URL = "https://tour.test.invalid/KorService2";

    @Test
    void mapsOnlyRequestedThemeFromNearbyTourismItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties();
        properties.setParkApiKey("TEST_KEY_ONLY");
        properties.setTourApiKey("TEST_KEY_ONLY");
        properties.setTourPageSize(200);
        TourApiScenicPlaceSource source = new TourApiScenicPlaceSource(
                builder.build(), properties);

        server.expect(requestTo(BASE_URL
                        + "/locationBasedList2?serviceKey=TEST_KEY_ONLY"
                        + "&MobileOS=ETC&MobileApp=just-go&_type=json&arrange=E"
                        + "&mapX=126.978000&mapY=37.566500&radius=20000"
                        + "&numOfRows=200&pageNo=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000"},
                            "body": {
                              "items": {
                                "item": [
                                  {
                                    "contentid": "SEA-1",
                                    "contenttypeid": "12",
                                    "title": "해안전망대 테스트",
                                    "mapx": "126.980000",
                                    "mapy": "37.568000"
                                  },
                                  {
                                    "contentid": "CITY-1",
                                    "contenttypeid": "14",
                                    "title": "도시박물관 테스트",
                                    "mapx": "126.981000",
                                    "mapy": "37.569000"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = source.findNearby(
                ScenicTheme.SEA,
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                75_000,
                20);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).contains("해안전망대");
            assertThat(candidate.theme()).isEqualTo(ScenicTheme.SEA);
            assertThat(candidate.source()).isEqualTo("TOUR_API");
        });
        server.verify();
    }
}
