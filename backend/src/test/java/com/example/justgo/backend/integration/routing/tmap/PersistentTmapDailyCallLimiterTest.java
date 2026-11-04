package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** 영속 TMAP 제한기의 날짜 전달과 상한 오류 변환을 검증합니다. */
class PersistentTmapDailyCallLimiterTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-04T12:00:00Z"), KOREA_ZONE);
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 4);

    /** 호출권이 남아 있으면 서울 날짜와 상한을 Mapper에 전달합니다. */
    @Test
    void reservesCallForKoreaDate() {
        TmapCallUsageMapper mapper = mock(TmapCallUsageMapper.class);
        when(mapper.reserveCall(FIXED_DATE, 90)).thenReturn(1);
        PersistentTmapDailyCallLimiter limiter =
                new PersistentTmapDailyCallLimiter(90, FIXED_CLOCK, mapper);

        limiter.acquire();

        verify(mapper).reserveCall(FIXED_DATE, 90);
    }

    /** DB가 예약 건수를 반환하지 않으면 외부 요청 전 쿼터 오류로 중단합니다. */
    @Test
    void rejectsCallWhenDatabaseLimitIsReached() {
        TmapCallUsageMapper mapper = mock(TmapCallUsageMapper.class);
        when(mapper.reserveCall(FIXED_DATE, 1)).thenReturn(null);
        PersistentTmapDailyCallLimiter limiter =
                new PersistentTmapDailyCallLimiter(1, FIXED_CLOCK, mapper);

        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
    }

    /** Free 상한보다 큰 설정값은 실행 전에 거부합니다. */
    @Test
    void rejectsInvalidDailyLimit() {
        TmapCallUsageMapper mapper = mock(TmapCallUsageMapper.class);

        assertThatThrownBy(() ->
                        new PersistentTmapDailyCallLimiter(101, FIXED_CLOCK, mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
