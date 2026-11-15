package com.example.justgo.backend.integration.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

    @Test
    void interleavesRegionsAndLimitsResultToTenEvents() {
        List<TodayEventSource.BannerEvent> seoul = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> event("서울 " + index, "서울"))
                .toList();
        List<TodayEventSource.BannerEvent> gyeonggi = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> event("경기 " + index, "경기"))
                .toList();

        List<TodayEventSource.BannerEvent> result = TodayEventSource.interleave(seoul, gyeonggi);

        assertThat(result).hasSize(10);
        assertThat(result).extracting(TodayEventSource.BannerEvent::region)
                .containsExactly("서울", "경기", "서울", "경기", "서울", "경기", "서울", "경기", "서울", "경기");
    }

    /** 최대 건수 테스트에 쓰는 개인정보 없는 행사 예시를 만든다. */
    private static TodayEventSource.BannerEvent event(String title, String region) {
        return new TodayEventSource.BannerEvent(title, region, "테스트 장소", "EVENT", null,
                37.5, 127.0, "TEST", "");
    }
}
