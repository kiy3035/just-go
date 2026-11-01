package com.example.justgo.backend.integration.routing.tmap;

/**
 * 인증키나 원시 위치를 포함하지 않고 TMAP 실패 유형만 전달하는 예외입니다.
 */
public class TmapRouteClientException extends RuntimeException {

    private final TmapClientErrorType errorType;

    public TmapRouteClientException(TmapClientErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public TmapRouteClientException(
            TmapClientErrorType errorType,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /**
     * 호출자에게 비밀값 없는 정규화 오류 유형을 반환합니다.
     */
    public TmapClientErrorType errorType() {
        return errorType;
    }
}
