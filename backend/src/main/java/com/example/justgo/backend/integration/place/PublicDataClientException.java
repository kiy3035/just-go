package com.example.justgo.backend.integration.place;

/** 공공데이터 원천의 인증·네트워크·응답 오류를 내부 폴백 대상으로 표시합니다. */
public class PublicDataClientException extends RuntimeException {

    public PublicDataClientException(String message) {
        super(message);
    }

    public PublicDataClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
