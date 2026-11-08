package com.example.justgo.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import com.example.justgo.backend.integration.routing.tmap.TmapClientErrorType;
import com.example.justgo.backend.integration.routing.tmap.TmapRouteClientException;
import com.example.justgo.backend.route.UnsupportedDrivingDurationException;
import com.example.justgo.backend.route.InvalidCandidateTokenException;
import com.example.justgo.backend.spot.InvalidSpotLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 입력 오류를 민감정보 없는 공통 JSON 응답으로 변환합니다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
     * TMAP 인증·쿼터·네트워크 오류를 비밀값이나 원시 응답 없이 안전한 API 오류로 변환합니다.
     */
    @ExceptionHandler(TmapRouteClientException.class)
    public ResponseEntity<ApiErrorResponse> handleTmapFailure(TmapRouteClientException exception) {
        // 비밀키·좌표·외부 응답은 남기지 않고 운영자가 조치할 실패 종류만 기록합니다.
        LOGGER.warn("외부 자동차 경로 제공자 호출 실패: errorType={}", exception.errorType());
        boolean temporarilyUnavailable = exception.errorType() == TmapClientErrorType.AUTHENTICATION
                || exception.errorType() == TmapClientErrorType.QUOTA_EXCEEDED;
        HttpStatus status = temporarilyUnavailable
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                "ROUTE_PROVIDER_UNAVAILABLE",
                "외부 자동차 경로 제공자 호출에 실패했습니다.",
                Map.of()));
    }

    /**
     * 장소 후보 조회 개수 위반을 필드 오류로 반환합니다.
     */
    @ExceptionHandler(InvalidSpotLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSpotLimit(InvalidSpotLimitException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_LIMIT",
                exception.getMessage(),
                Map.of("limit", exception.getMessage())));
    }

    /** 만료되거나 다른 조건에서 발급된 장소 후보 토큰을 400 오류로 반환합니다. */
    @ExceptionHandler(InvalidCandidateTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCandidateToken(
            InvalidCandidateTokenException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_CANDIDATE_TOKEN",
                exception.getMessage(),
                Map.of("candidateToken", exception.getMessage())));
    }

    /**
     * 필수 쿼리 파라미터 누락이나 알 수 없는 풍경 테마를 일관된 400 오류로 처리합니다.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidQueryParameter(Exception exception) {
        String parameterName = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ((MissingServletRequestParameterException) exception).getParameterName();
        String message = "쿼리 파라미터를 확인해 주세요: " + parameterName;
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("INVALID_PARAMETER", message, Map.of(parameterName, message)));
    }

    /** 후보 조회 GET의 좌표·시간·개수 제약 위반을 원시 바인딩 정보 없이 반환합니다. */
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMethodValidation() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_REQUEST",
                "요청값의 좌표·시간·조회 개수를 확인해 주세요.",
                Map.of()));
    }

    /**
     * 잘못된 JSON이나 알 수 없는 테마·주행 방식 문자열은 내부 역직렬화 내용을 노출하지 않고 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse("INVALID_JSON", "JSON 형식과 풍경 테마·주행 방식 값을 확인해 주세요.", Map.of()));
    }
}
