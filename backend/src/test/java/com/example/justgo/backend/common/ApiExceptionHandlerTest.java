package com.example.justgo.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.justgo.backend.integration.routing.tmap.TmapClientErrorType;
import com.example.justgo.backend.integration.routing.tmap.TmapRouteClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

/** 외부 경로 제공자 실패가 비밀값 없는 공통 오류 계약으로 변환되는지 검증합니다. */
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /** 인증 실패는 재설정이 필요한 일시 사용 불가 상태로 반환합니다. */
    @Test
    void mapsAuthenticationFailureToServiceUnavailable(CapturedOutput output) {
        var response = handler.handleTmapFailure(new TmapRouteClientException(
                TmapClientErrorType.AUTHENTICATION,
                "TEST_SECRET_MUST_NOT_BE_EXPOSED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ROUTE_PROVIDER_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("TEST_SECRET");
        assertThat(output).contains("errorType=AUTHENTICATION");
        assertThat(output).doesNotContain("TEST_SECRET_MUST_NOT_BE_EXPOSED");
    }

    /** 네트워크 실패는 외부 게이트웨이 오류로 구분합니다. */
    @Test
    void mapsNetworkFailureToBadGateway() {
        var response = handler.handleTmapFailure(new TmapRouteClientException(
                TmapClientErrorType.NETWORK,
                "TEST_COORDINATE_MUST_NOT_BE_EXPOSED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).doesNotContain("TEST_COORDINATE");
    }
}
