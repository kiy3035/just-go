package com.example.justgo.backend.integration.routing.tmap;

import java.time.Clock;
import java.time.LocalDate;

/**
 * PostgreSQL의 원자적 증가를 사용해 서버 재시작과 동시 요청에도 TMAP 상한을 유지합니다.
 * 날짜와 예약 건수만 저장하며 앱 키·좌표·경로·응답은 저장하지 않습니다.
 */
public class PersistentTmapDailyCallLimiter implements TmapCallLimiter {

    private final int dailyLimit;
    private final Clock clock;
    private final TmapCallUsageMapper callUsageMapper;

    public PersistentTmapDailyCallLimiter(
            int dailyLimit,
            Clock clock,
            TmapCallUsageMapper callUsageMapper) {
        if (dailyLimit < 1 || dailyLimit > 100) {
            throw new IllegalArgumentException("TMAP 일일 호출 상한은 1 이상 100 이하여야 합니다.");
        }
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.callUsageMapper = callUsageMapper;
    }

    /** 외부 HTTP 요청 전에 오늘 날짜의 호출권을 DB에서 한 건 예약합니다. */
    @Override
    public void acquire() {
        Integer usedCalls = callUsageMapper.reserveCall(LocalDate.now(clock), dailyLimit);
        if (usedCalls == null) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.QUOTA_EXCEEDED,
                    "TMAP 로컬 일일 호출 상한에 도달했습니다.");
        }
    }
}
