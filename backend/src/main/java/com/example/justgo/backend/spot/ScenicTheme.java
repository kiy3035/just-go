package com.example.justgo.backend.spot;

/**
 * 사용자가 선택할 수 있는 확정 풍경 테마입니다.
 */
public enum ScenicTheme {
    CITY,
    MOUNTAIN,
    RIVER,
    LAKE,
    SEA,
    PARK,
    ANYWHERE,
    WATER;

    public boolean includes(ScenicTheme theme) {
        return this == ANYWHERE || this == theme
                || (this == WATER && (theme == RIVER || theme == LAKE));
    }
}
