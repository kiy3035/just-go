package com.example.justgo.backend.integration.routing.tmap;

/**
 * TMAP 다중 경유지 요청에 전달하는 순서가 고정된 경유지입니다.
 */
public record TmapWaypoint(String id, String name, TmapCoordinate coordinate) {

    private static final int MAX_TEXT_LENGTH = 100;

    /**
     * 지나치게 크거나 비어 있는 식별자와 명칭이 외부 요청으로 전송되지 않게 검증합니다.
     */
    public TmapWaypoint {
        validateText(id, "경유지 식별자");
        validateText(name, "경유지 이름");
        if (coordinate == null) {
            throw new IllegalArgumentException("경유지 좌표가 필요합니다.");
        }
    }

    private static void validateText(String value, String fieldName) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + "은 1자 이상 100자 이하여야 합니다.");
        }
    }
}
