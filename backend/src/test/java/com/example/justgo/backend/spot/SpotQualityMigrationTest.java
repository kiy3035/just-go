package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 기존 V3 DB에 경관 품질 점수 제약을 안전하게 추가하는 V4 업그레이드를 검증합니다. */
@Testcontainers
class SpotQualityMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    /**
     * 출처 검수는 끝났지만 점수가 없던 기존 행을 보존하고 새 VERIFIED 행에는 점수를 강제합니다.
     */
    @Test
    void upgradesLegacyVerifiedRowsAndEnforcesQualityScores() throws SQLException {
        flywayAtVersionThree().migrate();
        insertLegacyVerifiedSpotWithProvenance();

        latestFlyway().migrate();

        assertThatLegacyRowWasPreserved();
        assertThatConstraintIsPendingLegacyValidation();
        assertThatThrownBy(this::insertNewVerifiedSpotWithoutQualityScores)
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    /** V3까지만 적용해 품질 점수 도입 전 로컬 DB 상태를 재현합니다. */
    private Flyway flywayAtVersionThree() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load();
    }

    /** 저장소의 최신 Flyway 마이그레이션을 적용합니다. */
    private Flyway latestFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load();
    }

    /** V3에서 유효했던 출처 검수 완료 행을 테스트 전용 값으로 만듭니다. */
    private void insertLegacyVerifiedSpotWithProvenance() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (
                    name, theme, latitude, longitude, verification_status, enabled,
                    source_name, source_reference, usage_rights_note, verified_at
                ) VALUES (
                    'LEGACY_QUALITY_TEST_ONLY', 'SEA', 0.100000, 0.200000, 'VERIFIED', TRUE,
                    'SOURCE_TEST_ONLY', 'https://example.invalid/source-test-only',
                    'USAGE_RIGHTS_TEST_ONLY', '2026-01-01T00:00:00Z'
                )
                """);
    }

    /** V4 적용 후 기존 행과 출처 이력이 임의 변경되지 않았는지 확인합니다. */
    private void assertThatLegacyRowWasPreserved() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT source_name, theme_fit_score, scenic_quality_score,
                            route_suitability_score, quality_review_note
                     FROM spot
                     WHERE name = 'LEGACY_QUALITY_TEST_ONLY'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("source_name")).isEqualTo("SOURCE_TEST_ONLY");
            assertThat(resultSet.getObject("theme_fit_score")).isNull();
            assertThat(resultSet.getObject("scenic_quality_score")).isNull();
            assertThat(resultSet.getObject("route_suitability_score")).isNull();
            assertThat(resultSet.getString("quality_review_note")).isNull();
        }
    }

    /** 기존 행 보완 전까지 품질 제약 전체 검증이 보류 상태인지 확인합니다. */
    private void assertThatConstraintIsPendingLegacyValidation() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT convalidated
                     FROM pg_constraint
                     WHERE conname = 'ck_spot_verified_quality'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean("convalidated")).isFalse();
        }
    }

    /** V4 이후 출처만 있고 품질 점수는 없는 새 VERIFIED 행 저장을 시도합니다. */
    private void insertNewVerifiedSpotWithoutQualityScores() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (
                    name, theme, latitude, longitude, verification_status, enabled,
                    source_name, source_reference, usage_rights_note, verified_at,
                    quality_review_note
                ) VALUES (
                    'NEW_MISSING_QUALITY_TEST_ONLY', 'MOUNTAIN', 0.300000, 0.400000, 'VERIFIED', TRUE,
                    'SOURCE_TEST_ONLY', 'https://example.invalid/source-test-only',
                    'USAGE_RIGHTS_TEST_ONLY', '2026-01-01T00:00:00Z',
                    'QUALITY_REVIEW_TEST_ONLY'
                )
                """);
    }

    /** 테스트 PostgreSQL에서 갱신 SQL을 실행합니다. */
    private void executeUpdate(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** 테스트 컨테이너의 임시 접속 정보로 JDBC 연결을 엽니다. */
    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword());
    }
}
