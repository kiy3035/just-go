package com.example.justgo.backend.spot;

/**
 * 장소 후보 조회 개수가 허용 범위를 벗어났을 때 발생합니다.
 */
public class InvalidSpotLimitException extends RuntimeException {

    public InvalidSpotLimitException(int limit) {
        super("장소 조회 개수는 1 이상 50 이하여야 합니다: " + limit);
    }
}
