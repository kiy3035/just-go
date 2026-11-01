package com.example.justgo.backend.integration.routing;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 실제 외부 경로 제공자가 연결되지 않은 일반 실행 환경의 안전한 기본 구현입니다.
 * 존재하지 않는 코스를 만들지 않고 빈 결과와 미연동 상태만 반환합니다.
 */
@Component
@Profile("!mock & !tmap")
public class UnavailableDrivingRouteProvider implements DrivingRouteProvider {

    /**
     * 외부 제공자가 없으므로 빈 결과를 반환합니다.
     */
    @Override
    public DrivingRouteResult calculate(DrivingRouteRequest request) {
        return new DrivingRouteResult(
                DrivingRouteMode.UNAVAILABLE,
                false,
                List.of(),
                "외부 자동차 경로 제공자가 아직 연결되지 않았습니다.");
    }
}
