package com.example.justgo.backend.integration.routing;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 브라우저 화면과 REST 연결만 확인하기 위한 개발용 MOCK 제공자입니다.
 * `mock` 프로필에서만 활성화되며 실제 도로·시간·거리 데이터로 사용하면 안 됩니다.
 */
@Component
@Profile("mock")
public class MockDrivingRouteProvider implements DrivingRouteProvider {

    private static final long MOCK_METERS_PER_MINUTE = 500L;

    /**
     * 요청값을 기반으로 명백히 MOCK이라고 표시된 예시 한 건을 생성합니다.
     */
    @Override
    public DrivingRouteResult calculate(DrivingRouteRequest request) {
        DrivingRouteCandidate mockRoute = new DrivingRouteCandidate(
                "MOCK-ROUTE-001",
                "MOCK 화면 연결 확인용 코스",
                request.desiredMinutes(),
                request.desiredMinutes() * MOCK_METERS_PER_MINUTE,
                request.theme(),
                "MOCK 예시이며 실제 도로 경로·예상 시간·거리가 아닙니다.");

        return new DrivingRouteResult(
                DrivingRouteMode.MOCK,
                true,
                List.of(mockRoute),
                "개발용 MOCK 모드입니다. 실제 경로 제공자 결과가 아닙니다.");
    }
}
