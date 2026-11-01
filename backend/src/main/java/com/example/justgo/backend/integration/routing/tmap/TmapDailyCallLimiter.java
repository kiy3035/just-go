package com.example.justgo.backend.integration.routing.tmap;

import java.time.Clock;
import java.time.LocalDate;

/**
 * TMAP Free의 일일 한도보다 낮은 로컬 상한을 적용하는 단일 서버용 호출량 보호 장치입니다.
 * 여러 서버로 확장할 경우에는 공유 저장소 기반 제한으로 교체해야 합니다.
 */
public class TmapDailyCallLimiter {

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
