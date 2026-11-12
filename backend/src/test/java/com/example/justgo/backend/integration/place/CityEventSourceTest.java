package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CityEventSourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 시작 시각은 포함하고 종료 시각부터는 추천에서 제외한다. */
    @Test
    void onlyRecommendsArrivalDuringKnownOpeningHours() throws Exception {
        var event = CityEventSource.parse(mapper.readTree("""
                {"eventNm":"TEST_EVENT","opar":"TEST_VENUE","eventStartDate":"2030-01-01",
                 "eventEndDate":"2030-01-01","eventStartTime":"18:00","eventEndTime":"19:00",
                 "latitude":"1.0","longitude":"2.0"}
                """)).orElseThrow();

        assertThat(event.canVisitAt(event.opens().minusSeconds(1))).isFalse();
        assertThat(event.canVisitAt(event.opens())).isTrue();
        assertThat(event.canVisitAt(event.closes().minusSeconds(1))).isTrue();
        assertThat(event.canVisitAt(event.closes())).isFalse();
    }

    /** 날짜만 있거나 당일 운영시간을 알 수 없는 여러 날 일정은 후보에서 제외한다. */
    @Test
    void rejectsAmbiguousSchedulesAndInvalidCoordinates() throws Exception {
        var incomplete = mapper.readTree("""
                {"eventNm":"TEST_EVENT","opar":"TEST_VENUE","eventStartDate":"2030-01-01",
                 "eventEndDate":"2030-01-02","eventStartTime":"18:00","eventEndTime":"19:00",
                 "latitude":"1.0","longitude":"2.0"}
                """);
        assertThat(CityEventSource.parse(incomplete)).isEmpty();
        var invalid = ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete)
                .put("eventEndDate", "2030-01-01").put("latitude", "91.0");
        assertThat(CityEventSource.parse(invalid)).isEmpty();
        invalid.put("latitude", "1.0").remove("eventEndTime");
        assertThat(CityEventSource.parse(invalid)).isEmpty();
    }
}
