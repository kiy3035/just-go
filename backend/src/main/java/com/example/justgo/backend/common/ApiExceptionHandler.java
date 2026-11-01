package com.example.justgo.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.justgo.backend.route.UnsupportedDrivingDurationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 입력 오류를 민감정보 없는 공통 JSON 응답으로 변환합니다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 좌표 범위나 필수값 위반을 필드별 메시지로 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("INVALID_REQUEST", "요청값을 확인해 주세요.", fieldErrors));
    }

    /**
     * 허용되지 않은 주행 시간 조건을 잘못된 요청으로 처리합니다.
     */
    @ExceptionHandler(UnsupportedDrivingDurationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedDuration(
            UnsupportedDrivingDurationException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "UNSUPPORTED_DURATION",
                exception.getMessage(),
                Map.of("desiredMinutes", exception.getMessage())));
    }

    /**
     * 잘못된 JSON이나 알 수 없는 테마 문자열은 내부 역직렬화 내용을 노출하지 않고 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse("INVALID_JSON", "JSON 형식과 풍경 테마 값을 확인해 주세요.", Map.of()));
    }
}
