package com.hazely.laborlens;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTests {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Test
    void migrateAndValidate() throws Exception {
        verify(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    // Verify migrations independently of application services and external API credentials.
    private static void verify(String url, String user, String password) throws Exception {
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations("filesystem:src/main/resources/db/migration")
                .cleanDisabled(true).baselineOnMigrate(false).load();
        flyway.migrate();
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (Connection db = DriverManager.getConnection(url, user, password)) {
            assertEquals(0, count(db, "SELECT COUNT(*) FROM source_file"));
            assertEquals(7, count(db, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name IN ('source_file', 'policy', 'job_run', "
                    + "'annual_employment_rate', 'monthly_unemployment_rate', "
                    + "'quarterly_employment_rate', 'quarterly_employment_count')"));

            // Fixtures simulate job registration; all source and observation rows are rolled back.
            db.setAutoCommit(false);
            try {
                String annual = "INSERT INTO annual_employment_rate "
                        + "(year, age_group, sex, education_attainment_level, nuts_2_region, employment_rate_percent) "
                        + "VALUES (2025, 'AGE_20_24', 'ALL', 'ALL', 'IRELAND', 0)";
                execute(db, annual);
                assertRejected(db, annual, 1062);
                assertRejected(db, annual.replace("2025", "2018"), 3819);
                assertRejected(db, annual.replace("AGE_20_24", "AGE_25_54"), 3819);
                assertRejected(db, annual.replace("'ALL', 'ALL'", "'ALL', 'NOT_STATED'"), 3819);
                assertRejected(db, annual.replace("'ALL', 'ALL'", "'ALL', 'BELOW_PRIMARY'"), 3819);
                assertRejected(db, annual.replace("'AGE_20_24'", "'all'"), 3819);
                assertRejected(db, annual.replace(", 0)", ", 101)"), 3819);
                assertRejected(db, annual.replace(", 0)", ", NULL)"), 1048);
                String monthly = "INSERT INTO monthly_unemployment_rate "
                        + "(year, month, age_group, sex, unemployment_rate_percent) VALUES (2025, 1, 'AGE_15_74', 'ALL', 0)";
                execute(db, monthly);
                assertRejected(db, monthly.replace("2025, 1", "2025, 13"), 3819);
                assertRejected(db, monthly.replace("AGE_15_74", "ALL"), 3819);
                String quarterly = "INSERT INTO quarterly_employment_rate "
                        + "(year, quarter, age_group, sex, education_attainment_level, employment_rate_percent) "
                        + "VALUES (2025, 1, 'AGE_25_54', 'ALL', 'ALL', 100)";
                execute(db, quarterly);
                assertRejected(db, quarterly.replace("2025, 1", "2025, 5"), 3819);
                String employed = "INSERT INTO quarterly_employment_count "
                        + "(year, quarter, citizenship, economic_sector, employed_persons_thousands) "
                        + "VALUES (2025, 1, 'OUTSIDE_EU_UK', 'INFORMATION_COMMUNICATION', 0)";
                execute(db, employed);
                assertRejected(db, employed.replace(", 0)", ", -1)"), 3819);
                assertRejected(db, employed.replace("OUTSIDE_EU_UK", "UNKNOWN"), 3819);
                // Multiple downloaded PDF editions can target the same policy table.
                for (String period : new String[]{"2024-2025", "2025-2028"}) {
                    String file = "statement-of-strategy-" + period + ".pdf";
                    execute(db, "INSERT INTO source_file (file_name, table_name, download_url, download_path, checksum) "
                            + "VALUES ('" + file + "', 'policy', 'https://example.org/" + file
                            + "', 'policies/" + file + "', REPEAT('a', 64))");
                }
                assertEquals(2, count(db, "SELECT COUNT(*) FROM source_file WHERE table_name = 'policy'"));
                verifyRuns(db);
                String policy = "INSERT INTO policy (period_start, period_end, policy, type, source_file_id) "
                        + "SELECT 2025, 2028, 'Test commitment', 'ECONOMIC_MIGRATION', id FROM source_file "
                        + "WHERE file_name = 'statement-of-strategy-2025-2028.pdf'";
                execute(db, policy);
                assertRejected(db, policy.replace("2025, 2028", "2028, 2025"), 3819);
                assertRejected(db, policy.replace("Test commitment", "   "), 3819);
                assertRejected(db, policy.replace("ECONOMIC_MIGRATION", "UNKNOWN"), 3819);
                assertRejected(db,
                        "INSERT INTO policy (period_start, period_end, policy, type, source_file_id) "
                        + "VALUES (2025, 2028, 'Test commitment', 'ECONOMIC_MIGRATION', 0)", 1452);
                assertRejected(db,
                        "DELETE FROM source_file WHERE file_name = 'statement-of-strategy-2025-2028.pdf'", 1451);
            } finally {
                db.rollback();
            }
            assertEquals(0, count(db, "SELECT COUNT(*) FROM source_file"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM job_run"));
        }
    }

    // Exercise pre-registration failures, version history and terminal state constraints.
    private static void verifyRuns(Connection db) throws SQLException {
        assertRejected(db, "UPDATE source_file SET checksum = 'invalid'", 3819);
        assertRejected(db, "UPDATE source_file SET checksum = NULL", 1048);
        String pending = "INSERT INTO job_run (job_type, file_name, status, started_at, process_version) "
                + "VALUES ('FILE_PREPARATION', 'missing.csv', 'RUNNING', '2026-09-29 00:00:00', 'v1')";
        execute(db, pending);
        execute(db, "UPDATE job_run SET status = 'FAILED', finished_at = '2026-09-29 00:01:00', "
                + "error_message = 'Download failed' WHERE file_name = 'missing.csv'");
        assertRejected(db, pending.replace("FILE_PREPARATION", "CSV_IMPORT"), 3819);
        assertRejected(db, pending.replace("FILE_PREPARATION", "UNKNOWN"), 3819);
        assertRejected(db, pending.replace("RUNNING", "SUCCESS"), 3819);
        assertRejected(db, pending.replace("RUNNING", "UNKNOWN"), 3819);
        assertRejected(db, pending.replace("'v1'", "' '"), 3819);
        String success = "INSERT INTO job_run (job_type, source_file_id, file_name, checksum, status, "
                + "started_at, finished_at, row_count, process_version) "
                + "SELECT 'POLICY_EXTRACTION', id, file_name, checksum, 'SUCCESS', "
                + "'2026-09-29 00:00:00', '2026-09-29 00:01:00', 0, 'v1' FROM source_file "
                + "WHERE file_name = 'statement-of-strategy-2024-2025.pdf'";
        execute(db, success);
        execute(db, success); // Forced runs retain separate history.
        assertRejected(db, success.replace("'SUCCESS'", "'RUNNING'"), 3819);
        assertRejected(db, success.replace("'2026-09-29 00:01:00'", "'2026-09-28 00:00:00'"), 3819);
        assertRejected(db, success.replace("id, file_name, checksum,", "0, file_name, checksum,"), 1452);
        assertRejected(db, success.replace("id, file_name, checksum,", "id, file_name, 'bad',"), 3819);
        execute(db, "UPDATE source_file SET checksum = REPEAT('b', 64) "
                + "WHERE file_name = 'statement-of-strategy-2024-2025.pdf'");
        assertEquals(2, count(db, "SELECT COUNT(*) FROM job_run WHERE checksum = REPEAT('a', 64)"));
        assertRejected(db, "DELETE FROM source_file WHERE file_name = 'statement-of-strategy-2024-2025.pdf'", 1451);
    }

    private static void assertRejected(Connection db, String sql, int code) {
        SQLException error = assertThrows(SQLException.class, () -> execute(db, sql));
        assertEquals(code, error.getErrorCode());
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int count(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
