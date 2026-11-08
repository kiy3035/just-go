package com.example.justgo.backend.route;

/** 만료됐거나 요청 테마·거리와 맞지 않는 후보 선택을 거부합니다. */
public class InvalidCandidateTokenException extends RuntimeException {

    public InvalidCandidateTokenException() {
        super("장소 후보가 만료됐거나 현재 검색 조건과 맞지 않습니다. 후보를 다시 조회해 주세요.");
    }
}
