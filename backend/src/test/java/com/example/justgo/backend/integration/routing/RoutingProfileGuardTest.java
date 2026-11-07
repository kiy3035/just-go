package com.example.justgo.backend.integration.routing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** 경로 제공자 프로필 조합이 MOCK과 LIVE 결과를 혼동시키지 않는지 검증합니다. */
class RoutingProfileGuardTest {

    /** mock 또는 tmap을 단독으로 사용하는 정상 실행은 허용합니다. */
    @Test
    void acceptsSingleProviderProfile() {
        assertThatCode(() -> RoutingProfileGuard.validateActiveProfiles(Set.of("local", "mock")))
                .doesNotThrowAnyException();
        assertThatCode(() -> RoutingProfileGuard.validateActiveProfiles(Set.of("local", "tmap")))
                .doesNotThrowAnyException();
    }

    /** mock과 tmap을 함께 켜면 서버가 모호한 제공자를 선택하기 전에 실패합니다. */
    @Test
    void rejectsMockAndTmapTogether() {
        assertThatThrownBy(() -> RoutingProfileGuard.validateActiveProfiles(
                Set.of("local", "mock", "tmap")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동시에 활성화할 수 없습니다");
    }
}
