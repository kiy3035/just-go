package com.example.justgo.backend.integration.routing.tmap;

/**
 * TMAP 호출 실패를 내부 추천 계층이 구분할 수 있도록 정규화한 오류 유형입니다.
 */
public enum TmapClientErrorType {
    AUTHENTICATION,
    QUOTA_EXCEEDED,
    NO_ROUTE,
    INVALID_REQUEST,
    NETWORK,
    UPSTREAM_FAILURE,
    INVALID_RESPONSE
}
