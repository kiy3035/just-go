package com.example.justgo.backend.integration.routing;

import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** MOCK 결과를 LIVE로 오인하지 않도록 상호 배타적인 경로 제공자 프로필을 검사합니다. */
@Component
public class RoutingProfileGuard {

    public RoutingProfileGuard(Environment environment) {
        validateActiveProfiles(Set.of(environment.getActiveProfiles()));
    }

    /** 개발용 mock과 실제 외부 호출용 tmap을 동시에 켜는 잘못된 실행을 거부합니다. */
    static void validateActiveProfiles(Set<String> activeProfiles) {
        if (activeProfiles.contains("mock") && activeProfiles.contains("tmap")) {
            throw new IllegalStateException(
                    "mock과 tmap 프로필을 동시에 활성화할 수 없습니다.");
        }
    }
}
