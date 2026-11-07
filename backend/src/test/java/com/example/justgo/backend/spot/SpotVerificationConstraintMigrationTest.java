package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V6가 과거 불완전 검수 행을 보존하면서 VERIFIED 제약을 완전 검증하는지 확인합니다. */
@Testcontainers
class SpotVerificationConstraintMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    /** 불완전 행만 PENDING으로 되돌리고 완전한 VERIFIED 행과 전체 제약은 유지합니다. */
    @Test
    void downgradesIncompleteLegacyRowsAndValidatesConstraints() throws SQLException {
        flywayAtVersionTwo().migrate();
        insertLegacyVerifiedSpot();
        flywayAtVersionFive().migrate();
        insertCompleteVerifiedSpot();

        latestFlyway().migrate();

        assertThat(statusOf("LEGACY_V6_TEST_ONLY")).isEqualTo("PENDING");
        assertThat(statusOf("COMPLETE_V6_TEST_ONLY")).isEqualTo("VERIFIED");
        assertThat(singleInt("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname IN ('ck_spot_verified_provenance', 'ck_spot_verified_quality')
                  AND convalidated = TRUE
                """)).isEqualTo(2);
        assertThat(singleInt("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '6' AND success = TRUE
                """)).isOne();
        assertThatThrownBy(() -> executeUpdate("""
                UPDATE spot
                SET verification_status = 'VERIFIED'
                WHERE name = 'LEGACY_V6_TEST_ONLY'
                """))
                .isInstanceOf(SQLException.class)
                .satisfies(error ->
                        assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    /** V2의 출처·품질 열이 없던 VERIFIED 행을 재현합니다. */
    private void insertLegacyVerifiedSpot() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (name, theme, latitude, longitude, verification_status, enabled)
                VALUES ('LEGACY_V6_TEST_ONLY', 'RIVER', 0.100000, 0.200000, 'VERIFIED', TRUE)
                """);
    }

    /** V5 기준을 모두 만족하는 검수 완료 행은 V6에서도 유지되는지 확인합니다. */
    private void insertCompleteVerifiedSpot() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (
                    name, theme, latitude, longitude, verification_status, enabled,
                    source_name, source_reference, usage_rights_note, verified_at,
                    theme_fit_score, scenic_quality_score, route_suitability_score,
                    quality_review_note
                ) VALUES (
                    'COMPLETE_V6_TEST_ONLY', 'CITY', 0.300000, 0.400000, 'VERIFIED', TRUE,
                    'SOURCE_TEST_ONLY', 'https://example.invalid/source-test-only',
                    'USAGE_RIGHTS_TEST_ONLY', '2026-01-01T00:00:00Z',
                    3, 4, 5, 'QUALITY_REVIEW_TEST_ONLY'
                )
                """);
    }

    /** V2까지만 적용해 레거시 행을 만들 수 있는 상태를 재현합니다. */
    private Flyway flywayAtVersionTwo() {
        return flywayAt(MigrationVersion.fromVersion("2"));
    }

    /** V6 적용 직전인 V5까지 마이그레이션합니다. */
    private Flyway flywayAtVersionFive() {
        return flywayAt(MigrationVersion.fromVersion("5"));
    }

    /** 저장소의 최신 마이그레이션을 적용합니다. */
    private Flyway latestFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load();
    }

    /** 지정 버전까지 적용하는 Flyway를 만듭니다. */
    private Flyway flywayAt(MigrationVersion version) {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(version)
                .load();
    }

    /** 이름으로 검수 상태를 조회합니다. */
    private String statusOf(String name) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT verification_status
                     FROM spot
                     WHERE name = ?
                     """)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    /** 테스트 PostgreSQL에서 갱신 SQL을 실행합니다. */
    private void executeUpdate(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** 한 행·한 정수 조회 결과를 반환합니다. */
    private int singleInt(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
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
