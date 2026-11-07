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

/** 기존 V2 DB에 출처 검수 제약을 안전하게 추가하는 V3 업그레이드를 검증합니다. */
@Testcontainers
class SpotProvenanceMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    /**
     * 기존 VERIFIED 행 때문에 V3가 실패하지 않고, 새 VERIFIED 행부터 출처를 강제하는지 확인합니다.
     */
    @Test
    void upgradesLegacyVerifiedRowsAndEnforcesNewWrites() throws SQLException {
        flywayAtVersionTwo().migrate();
        insertLegacyVerifiedSpot();

        flywayAtVersionThree().migrate();

        assertThatLegacyRowWasPreserved();
        assertThatConstraintIsPendingLegacyValidation();
        assertThatThrownBy(this::insertNewVerifiedSpotWithoutProvenance)
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    /** V2까지만 적용해 기존 로컬 DB 상태를 재현합니다. */
    private Flyway flywayAtVersionTwo() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load();
    }

    /** 출처 제약을 처음 도입한 V3까지만 적용합니다. */
    private Flyway flywayAtVersionThree() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load();
    }

    /** V2에는 허용됐던 출처 없는 검수 완료 행을 테스트 전용 값으로 만듭니다. */
    private void insertLegacyVerifiedSpot() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (name, theme, latitude, longitude, verification_status, enabled)
                VALUES ('LEGACY_VERIFIED_TEST_ONLY', 'RIVER', 0.100000, 0.200000, 'VERIFIED', TRUE)
                """);
    }

    /** V3 적용 후 기존 행이 임의 삭제·변경되지 않았는지 확인합니다. */
    private void assertThatLegacyRowWasPreserved() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT verification_status, source_name, verified_at
                     FROM spot
                     WHERE name = 'LEGACY_VERIFIED_TEST_ONLY'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("verification_status")).isEqualTo("VERIFIED");
            assertThat(resultSet.getString("source_name")).isNull();
            assertThat(resultSet.getObject("verified_at")).isNull();
        }
    }

    /** 기존 행 보완 전까지 제약 검증이 보류 상태인지 확인합니다. */
    private void assertThatConstraintIsPendingLegacyValidation() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT convalidated
                     FROM pg_constraint
                     WHERE conname = 'ck_spot_verified_provenance'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean("convalidated")).isFalse();
        }
    }

    /** V3 이후 출처 없는 새 VERIFIED 행 저장을 시도합니다. */
    private void insertNewVerifiedSpotWithoutProvenance() throws SQLException {
        executeUpdate("""
                INSERT INTO spot (name, theme, latitude, longitude, verification_status, enabled)
                VALUES ('NEW_MISSING_PROVENANCE_TEST_ONLY', 'CITY', 0.300000, 0.400000, 'VERIFIED', TRUE)
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
