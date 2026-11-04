package com.example.justgo.backend.integration.routing.tmap;

/** 실제 외부 요청 전에 TMAP 무료 호출권을 한 건 예약하는 경계입니다. */
public interface TmapCallLimiter {

    /** 일일 상한 안이면 호출권을 예약하고, 초과하면 외부 요청 전에 예외를 던집니다. */
    void acquire();
}
