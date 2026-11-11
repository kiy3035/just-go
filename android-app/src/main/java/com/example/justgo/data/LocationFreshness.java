package com.example.justgo.data;

/** 위치 객체와 무관하게 최근 좌표의 사용 가능 여부를 판단하는 정책입니다. */
public final class LocationFreshness {
    public static final long MAX_AGE_NANOS = 30_000_000_000L;

    private LocationFreshness() { }

    public static boolean isUsable(
            long nowElapsedRealtimeNanos,
            long fixElapsedRealtimeNanos,
            double latitude,
            double longitude) {
        long age = nowElapsedRealtimeNanos - fixElapsedRealtimeNanos;
        return age >= 0 && age <= MAX_AGE_NANOS
                && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }
}
