package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 17에서 TMAP 호출권이 재시작·동시 요청에도 상한을 지키는지 검증합니다. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("tmap")
class TmapCallUsageMapperIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private TmapCallUsageMapper callUsageMapper;

    /** 테스트 컨테이너가 만든 임시 접속 정보만 Spring 데이터소스에 전달합니다. */
    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("integration.tmap.app-key", () -> "TEST_APP_KEY_ONLY");
    }

    /** 새 제한기 인스턴스도 DB에 남은 오늘 사용량을 이어서 적용합니다. */
    @Test
    void keepsUsageAcrossLimiterInstances() {
        Clock clock = fixedClock("2026-11-04T01:00:00Z");
        LocalDate date = LocalDate.of(2026, 11, 4);
        TmapCallLimiter first = new PersistentTmapDailyCallLimiter(1, clock, callUsageMapper);
        TmapCallLimiter afterRestart =
                new PersistentTmapDailyCallLimiter(1, clock, callUsageMapper);

        first.acquire();

        assertThatThrownBy(afterRestart::acquire)
                .isInstanceOfSatisfying(TmapRouteClientException.class, exception ->
                        assertThat(exception.errorType())
                                .isEqualTo(TmapClientErrorType.QUOTA_EXCEEDED));
        assertThat(callUsageMapper.findUsedCalls(date)).isEqualTo(1);
    }

    /** 여러 요청이 동시에 예약해도 성공 건수는 설정한 일일 상한을 넘지 않습니다. */
    @Test
    void reservesAtomicallyUnderConcurrentRequests() throws Exception {
        Clock clock = fixedClock("2026-11-05T01:00:00Z");
        LocalDate date = LocalDate.of(2026, 11, 5);
        TmapCallLimiter limiter = new PersistentTmapDailyCallLimiter(3, clock, callUsageMapper);
        List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> (Callable<Boolean>) () -> {
                    try {
                        limiter.acquire();
                        return true;
                    } catch (TmapRouteClientException exception) {
                        return false;
                    }
                })
                .toList();

        List<Boolean> results;
        try (var executor = Executors.newFixedThreadPool(10)) {
            results = executor.invokeAll(attempts).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException("동시 호출권 테스트 결과를 읽지 못했습니다.", exception);
                }
            }).toList();
        }

        assertThat(results).containsExactlyInAnyOrder(
                true, true, true, false, false, false, false, false, false, false);
        assertThat(callUsageMapper.findUsedCalls(date)).isEqualTo(3);
    }

    /** 서울 시간대를 사용하는 고정 시계를 만듭니다. */
    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), KOREA_ZONE);
    }
}
