package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        spotMapper.insertCandidate(
                "VERIFIED_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.100000"),
                new BigDecimal("0.200000"),
                SpotVerificationStatus.VERIFIED,
                true);
        spotMapper.insertCandidate(
                "PENDING_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.300000"),
                new BigDecimal("0.400000"),
                SpotVerificationStatus.PENDING,
                true);
        spotMapper.insertCandidate(
                "DISABLED_RIVER_TEST_ONLY",
                ScenicTheme.RIVER,
                new BigDecimal("0.500000"),
                new BigDecimal("0.600000"),
                SpotVerificationStatus.VERIFIED,
                false);

        List<SpotCandidateRecord> candidates =
                spotMapper.findVerifiedCandidatesByTheme(ScenicTheme.RIVER, 10);

        assertThat(candidates)
                .extracting(SpotCandidateRecord::name)
                .contains("VERIFIED_RIVER_TEST_ONLY")
                .doesNotContain("PENDING_RIVER_TEST_ONLY", "DISABLED_RIVER_TEST_ONLY");
    }
}
