package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TodayEventSourceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final LocalDate today = LocalDate.of(2026, 11, 14);

    @Test
    void seoulEventUsesLocalDatesAndClosingTime() throws Exception {
        var row = json.readTree("""
                {"TITLE":"전시 A","PLACE":"전시장 A","GUNAME":"종로구","CODENAME":"전시/미술",
                 "STRTDATE":"2026-11-13 00:00:00.0","END_DATE":"2026-11-14 00:00:00.0",
                 "LAT":"37.56","LOT":"126.98","MAIN_IMG":"https://example.org/exhibit.jpg",
                 "PRO_TIME":"9:00~18:00"}
                """);
        var event = TodayEventSource.parse(row, today, true).orElseThrow();
        assertThat(event.closes()).isEqualTo(LocalTime.of(18, 0));
        assertThat(event.banner().region()).isEqualTo("서울 종로구");
        assertThat(event.banner().category()).isEqualTo("EXHIBITION");
        assertThat(event.banner().imageUrl()).isEqualTo("https://example.org/exhibit.jpg");
        assertThat(TodayEventSource.parse(row, today.plusDays(1), true)).isEmpty();
    }

    @Test
    void gyeonggiEventWithoutImageKeepsItsDestination() throws Exception {
        var row = json.readTree("""
                {"title":"축제 A","addr1":"경기도 행사장 A","cat3":"A02070100",
                 "eventstartdate":"20261101","eventenddate":"20261130",
                 "mapy":"37.25","mapx":"127.02","firstimage":""}
                """);
        var event = TodayEventSource.parse(row, today, false).orElseThrow();
        assertThat(event.banner().imageUrl()).isNull();
        assertThat(event.banner().category()).isEqualTo("FESTIVAL");
        assertThat(event.banner().longitude()).isEqualTo(127.02);
    }

    @Test
    void rejectsSwappedCoordinates() throws Exception {
        var row = json.readTree("""
                {"title":"행사 A","addr1":"행사장 A","eventstartdate":"20261114",
                 "eventenddate":"20261114","mapy":"127.02","mapx":"37.25"}
                """);
        assertThat(TodayEventSource.parse(row, today, false)).isEmpty();
    }
}
