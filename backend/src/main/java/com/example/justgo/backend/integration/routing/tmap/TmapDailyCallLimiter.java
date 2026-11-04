package com.example.justgo.backend.integration.routing.tmap;

import java.time.Clock;
import java.time.LocalDate;

/**
 * DB가 없는 HTTP 단위 테스트에서 TMAP 일일 상한을 검증하는 메모리 보호 장치입니다.
 * 실제 tmap 프로필은 서버 재시작에도 사용량을 유지하는 영속 제한기를 사용합니다.
 */
public class TmapDailyCallLimiter implements TmapCallLimiter {

    private final int dailyLimit;
    private final Clock clock;
    private LocalDate currentDate;
    private int usedCalls;

    public TmapDailyCallLimiter(int dailyLimit, Clock clock) {
        if (dailyLimit < 1 || dailyLimit > 100) {
            throw new IllegalArgumentException("TMAP 일일 호출 상한은 1 이상 100 이하여야 합니다.");
        }
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.currentDate = LocalDate.now(clock);
    }

    /**
     * 외부 요청 직전에 호출권을 예약하고 날짜가 바뀌면 로컬 집계를 초기화합니다.
     */
    @Override
    public synchronized void acquire() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(currentDate)) {
            currentDate = today;
            usedCalls = 0;
        }
        if (usedCalls >= dailyLimit) {
            throw new TmapRouteClientException(
                    TmapClientErrorType.QUOTA_EXCEEDED,
                    "TMAP 로컬 일일 호출 상한에 도달했습니다.");
        }
        usedCalls++;
    }
}
