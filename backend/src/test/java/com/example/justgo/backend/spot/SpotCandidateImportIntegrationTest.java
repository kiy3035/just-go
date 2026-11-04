package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** candidate-import 프로필이 가짜 로컬 JSON을 PostgreSQL 17에 등록하는 전체 흐름을 검증합니다. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("candidate-import")
class SpotCandidateImportIntegrationTest {

    private static final Path IMPORT_FILE = createImportFile();

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private SpotMapper spotMapper;

    @Autowired
    private SpotCandidateImportService importService;

    /** 테스트 DB와 가짜 JSON 파일만 candidate-import 프로필에 전달합니다. */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("justgo.spot-import.path", IMPORT_FILE::toString);
    }

    /** 프로필 시작 시 검수 필드가 모두 있는 후보가 추천 조회에 들어오는지 확인합니다. */
    @Test
    void importsLocalJsonIntoPostgresql() {
        assertThat(spotMapper.findVerifiedCandidatesByTheme(ScenicTheme.SEA, 10))
                .extracting(SpotCandidateRecord::name)
                .containsExactly("IMPORT_INTEGRATION_TEST_ONLY");
    }

    /** 사전 검증은 PostgreSQL 중복 조회만 수행하고 신규 후보를 저장하지 않습니다. */
    @Test
    void dryRunDoesNotWriteToPostgresql() {
        SpotCandidateImportEntry entry = new SpotCandidateImportEntry(
                "DRY_RUN_INTEGRATION_TEST_ONLY",
                ScenicTheme.LAKE,
                new BigDecimal("0.300000"),
                new BigDecimal("0.400000"),
                "SOURCE_TEST_ONLY",
                "https://example.invalid/dry-run-integration-test",
                "USAGE_RIGHTS_TEST_ONLY",
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
                3,
                4,
                5,
                "QUALITY_REVIEW_TEST_ONLY");

        SpotCandidateImportValidationResult result =
                importService.validateCandidates(List.of(entry));

        assertThat(result.readyCount()).isOne();
        assertThat(spotMapper.countCandidateIdentity(
                        entry.name(), entry.sourceName(), entry.sourceReference()))
                .isZero();
    }

    /** 실제 위치와 혼동되지 않는 가짜 JSON을 임시 파일로 만들고 종료 시 제거합니다. */
    private static Path createImportFile() {
        try {
            Path path = Files.createTempFile("just-go-candidate-import-test-", ".json");
            Files.writeString(path, """
                    {
                      "candidates": [
                        {
                          "name": "IMPORT_INTEGRATION_TEST_ONLY",
                          "theme": "SEA",
                          "latitude": 0.100000,
                          "longitude": 0.200000,
                          "sourceName": "SOURCE_TEST_ONLY",
                          "sourceReference": "https://example.invalid/import-integration-test",
                          "usageRightsNote": "USAGE_RIGHTS_TEST_ONLY",
                          "verifiedAt": "2026-11-01T00:00:00Z",
                          "themeFitScore": 3,
                          "scenicQualityScore": 4,
                          "routeSuitabilityScore": 5,
                          "qualityReviewNote": "QUALITY_REVIEW_TEST_ONLY"
                        }
                      ]
                    }
                    """);
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
