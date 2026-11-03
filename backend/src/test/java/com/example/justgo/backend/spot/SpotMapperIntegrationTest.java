package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL 17 컨테이너에서 Flyway와 MyBatis XML Mapper를 함께 검증합니다.
 */
@Testcontainers
@SpringBootTest
class SpotMapperIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private SpotMapper spotMapper;

    /**
     * 테스트 컨테이너가 만든 임시 접속 정보를 Spring 데이터소스에 전달합니다.
     */
    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    /**
     * Flyway가 만든 테이블에 #{...} 파라미터로 저장하고 XML 결과 매핑으로 다시 읽습니다.
     */
    @Test
    void insertsAndReadsSpotThroughXmlMapper() {
        String spotName = "MYBATIS_BINDING_TEST_ONLY";

        int insertedRows = spotMapper.insert(spotName, ScenicTheme.RIVER);
        long id = spotMapper.findLatestIdByName(spotName).orElseThrow();
        SpotRecord saved = spotMapper.findById(id).orElseThrow();

        assertThat(insertedRows).isOne();
        assertThat(saved.name()).isEqualTo(spotName);
        assertThat(saved.theme()).isEqualTo(ScenicTheme.RIVER);
    }

    /**
     * V2 좌표·검수 상태가 적용되고 VERIFIED·활성 후보만 조회되는지 확인합니다.
     */
    @Test
    void findsOnlyEnabledAndVerifiedCandidates() {
        spotMapper.insertCandidate(candidate(
                "VERIFIED_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000"),
                SpotVerificationStatus.VERIFIED,
                true));
        spotMapper.insertCandidate(candidate(
                "PENDING_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.300000"),
                new BigDecimal("0.400000"),
                SpotVerificationStatus.PENDING,
                true));
        spotMapper.insertCandidate(candidate(
                "DISABLED_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.500000"),
                new BigDecimal("0.600000"),
                SpotVerificationStatus.VERIFIED,
                false));

        List<SpotCandidateRecord> candidates =
                spotMapper.findVerifiedCandidatesByTheme(ScenicTheme.RIVER, 10);

        assertThat(candidates)
                .extracting(SpotCandidateRecord::name)
                .contains("VERIFIED_RIVER_TEST_ONLY")
                .doesNotContain("PENDING_RIVER_TEST_ONLY", "DISABLED_RIVER_TEST_ONLY");
    }

    /** VERIFIED 후보에 출처·이용권한·검수 시각이 없으면 DB가 저장을 거부하는지 확인합니다. */
    @Test
    void rejectsVerifiedCandidateWithoutProvenance() {
        SpotCandidateRegistration missingProvenance = new SpotCandidateRegistration(
                "MISSING_PROVENANCE_TEST_ONLY",
                ScenicTheme.CITY,
                new BigDecimal("0.700000"),
                new BigDecimal("0.800000"),
                SpotVerificationStatus.VERIFIED,
                true,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> spotMapper.insertCandidate(missingProvenance))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 실제 장소·권리 정보와 혼동되지 않는 테스트 전용 후보를 생성합니다. */
    private SpotCandidateRegistration candidate(
            String name,
            ScenicTheme theme,
            BigDecimal latitude,
            BigDecimal longitude,
            SpotVerificationStatus verificationStatus,
            boolean enabled) {
        if (verificationStatus != SpotVerificationStatus.VERIFIED) {
            return new SpotCandidateRegistration(
                    name,
                    theme,
                    latitude,
                    longitude,
                    verificationStatus,
                    enabled,
                    null,
                    null,
                    null,
                    null);
        }
        return new SpotCandidateRegistration(
                name,
                theme,
                latitude,
                longitude,
                verificationStatus,
                enabled,
                "SOURCE_TEST_ONLY",
                "https://example.invalid/source-test-only",
                "USAGE_RIGHTS_TEST_ONLY",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }
}
