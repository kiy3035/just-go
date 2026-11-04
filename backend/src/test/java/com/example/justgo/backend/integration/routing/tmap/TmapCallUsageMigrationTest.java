package com.example.justgo.backend.integration.routing.tmap;

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

/** 기존 V4 로컬 DB를 V5 호출량 테이블로 안전하게 올리는 과정을 검증합니다. */
@Testcontainers
class TmapCallUsageMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    /** 기존 장소 행을 보존하면서 빈 호출량 테이블과 100건 DB 상한을 추가합니다. */
    @Test
    void upgradesVersionFourDatabaseWithoutChangingSpotData() throws SQLException {
        flywayAtVersionFour().migrate();
        executeUpdate("""
                INSERT INTO spot (name, theme)
                VALUES ('V5_MIGRATION_SPOT_TEST_ONLY', 'CITY')
                """);

        latestFlyway().migrate();

        assertThat(singleInt("""
                SELECT COUNT(*) FROM spot
                WHERE name = 'V5_MIGRATION_SPOT_TEST_ONLY'
                """)).isOne();
        assertThat(singleInt("SELECT COUNT(*) FROM tmap_daily_call_usage")).isZero();
        assertThat(singleInt("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '5' AND success = TRUE
                """)).isOne();
        assertThatThrownBy(() -> executeUpdate("""
                INSERT INTO tmap_daily_call_usage (usage_date, used_calls)
                VALUES ('2026-11-04', 101)
                """))
                .isInstanceOf(SQLException.class)
                .satisfies(error ->
                        assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    /** V4까지만 적용해 호출량 테이블 도입 전 로컬 DB 상태를 재현합니다. */
    private Flyway flywayAtVersionFour() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .target(MigrationVersion.fromVersion("4"))
                .load();
    }

    /** 저장소의 최신 Flyway 마이그레이션을 적용합니다. */
    private Flyway latestFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .load();
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
