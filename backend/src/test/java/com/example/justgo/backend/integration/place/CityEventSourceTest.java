package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CityEventSourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 시작 시각은 포함하고 종료 시각부터는 추천에서 제외한다. */
    @Test
    void onlyRecommendsArrivalDuringKnownOpeningHours() throws Exception {
        var schedule = CityEventSource.parse(mapper.readTree("""
                {"eventNm":"TEST_EVENT","opar":"TEST_VENUE","eventStartDate":"2030-01-01",
                 "eventEndDate":"2030-01-01","eventStartTime":"18:00","eventEndTime":"19:00",
                 "latitude":"1.0","longitude":"2.0"}
                """)).orElseThrow();
        var event = schedule.occurrenceOn(java.time.LocalDate.of(2030, 1, 1)).orElseThrow();

        assertThat(event.canVisitAt(event.opens().minusSeconds(1))).isFalse();
        assertThat(event.canVisitAt(event.opens())).isTrue();
        assertThat(event.canVisitAt(event.closes().minusSeconds(1))).isTrue();
        assertThat(event.canVisitAt(event.closes())).isFalse();
    }

    /** 시작일부터 종료일까지 모든 날짜에 해당 날짜의 운영시간을 적용한다. */
    @Test
    void includesEveryDateInMultiDaySchedule() throws Exception {
        var schedule = CityEventSource.parse(mapper.readTree("""
                {"eventNm":"TEST_EVENT","opar":"TEST_VENUE","eventStartDate":"2030-01-01",
                 "eventEndDate":"2030-01-03","eventStartTime":"10:00","eventEndTime":"18:00",
                 "latitude":"1.0","longitude":"2.0"}
                """)).orElseThrow();

        assertThat(schedule.occurrenceOn(java.time.LocalDate.of(2029, 12, 31))).isEmpty();
        assertThat(schedule.occurrenceOn(java.time.LocalDate.of(2030, 1, 1))).isPresent();
        var middle = schedule.occurrenceOn(java.time.LocalDate.of(2030, 1, 2)).orElseThrow();
        assertThat(middle.opens().toLocalDate()).isEqualTo(java.time.LocalDate.of(2030, 1, 2));
        assertThat(middle.opens().toLocalTime()).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(middle.closes().toLocalTime()).isEqualTo(java.time.LocalTime.of(18, 0));
        assertThat(schedule.occurrenceOn(java.time.LocalDate.of(2030, 1, 3))).isPresent();
        assertThat(schedule.occurrenceOn(java.time.LocalDate.of(2030, 1, 4))).isEmpty();
    }

    /** 잘못된 날짜 범위, 좌표 또는 운영시간은 후보에서 제외한다. */
    @Test
    void rejectsInvalidSchedulesAndCoordinates() throws Exception {
        var invalid = ((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
                {"eventNm":"TEST_EVENT","opar":"TEST_VENUE","eventStartDate":"2030-01-02",
                 "eventEndDate":"2030-01-01","eventStartTime":"18:00","eventEndTime":"19:00",
                 "latitude":"1.0","longitude":"2.0"}
                """));
        assertThat(CityEventSource.parse(invalid)).isEmpty();
        invalid.put("eventStartDate", "2030-01-01").put("latitude", "91.0");
        assertThat(CityEventSource.parse(invalid)).isEmpty();
        invalid.put("latitude", "1.0").remove("eventEndTime");
        assertThat(CityEventSource.parse(invalid)).isEmpty();
    }
}
