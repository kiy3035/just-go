package com.example.justgo.backend.common;

import java.util.Map;

/**
 * 클라이언트가 입력 오류를 일관되게 표시할 수 있도록 제공하는 오류 응답입니다.
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors) {

    /**
     * 필드 오류가 이후 변경되지 않도록 방어적으로 복사합니다.
     */
    public ApiErrorResponse {
        fieldErrors = Map.copyOf(fieldErrors);
    }
}
