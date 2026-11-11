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
    WATER,
    CAFE,
    KOREAN,
    CHINESE,
    JAPANESE,
    WESTERN;

    public boolean isDining() {
        return switch (this) {
            case CAFE, KOREAN, CHINESE, JAPANESE, WESTERN -> true;
            default -> false;
        };
    }

    public boolean includes(ScenicTheme theme) {
        return (this == ANYWHERE && !theme.isDining()) || this == theme
                || (this == WATER && (theme == RIVER || theme == LAKE));
    }
}
