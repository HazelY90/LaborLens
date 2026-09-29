package com.hazely.laborlens.jobs;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.jobs.metricsExtraction.CsvJob;
import com.hazely.laborlens.jobs.metricsExtraction.CsvParserTests;
import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import com.hazely.laborlens.jobs.filePrepare.FileJob;
import com.hazely.laborlens.jobs.filePrepare.FileDownload;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated MySQL database; never imports into the configured application database. */
@Testcontainers(disabledWithoutDocker = true)
class CsvJobTests {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @TempDir
    Path root;

    @Test
    void preservesSnapshotsAndRunHistory() throws Exception {
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var sql = new JdbcTemplate(dataSource);
        var store = new JobStore(dataSource);
        var files = new JobFiles(root.toString());
        var job = new CsvJob(store, files);
        var prepare = new FileJob(store, files, new FileDownload());
        Files.createDirectories(root.resolve("cso"));
        Path file = root.resolve("cso/ALF01.csv");
        String original = CsvParserTests.sample();
        Files.writeString(file, original);

        prepare.run(CsvSource.ALF01, false);
        var initial = job.importFile(CsvSource.ALF01, false);
        assertEquals(JobStatus.SUCCESS, initial.status());
        assertEquals(7L, initial.rowCount());
        Long source = sql.queryForObject("SELECT id FROM source_file", Long.class);
        String checksum = sql.queryForObject("SELECT checksum FROM source_file", String.class);
        assertEquals(original, Files.readString(root.resolve("runs/" + initial.runId() + "/ALF01.csv")));
        assertEquals(JobStatus.SKIPPED, job.importFile(CsvSource.ALF01, false).status());
        assertEquals(JobStatus.SUCCESS, job.importFile(CsvSource.ALF01, true).status());
        assertEquals(source, sql.queryForObject("SELECT id FROM source_file", Long.class));
        assertEquals(7L, sql.queryForObject("SELECT COUNT(*) FROM annual_employment_rate", Long.class));

        // A changed parser version must be processed even if the input bytes match.
        sql.update("UPDATE job_run SET process_version = 'csv-v0' WHERE job_type = 'CSV_IMPORT'");
        assertEquals(JobStatus.SUCCESS, job.importFile(CsvSource.ALF01, false).status());

        Files.writeString(file, original.replace(",%,10", ",%,20"));
        assertThrows(IllegalStateException.class, () -> job.importFile(CsvSource.ALF01, false));
        assertEquals(checksum, sql.queryForObject("SELECT checksum FROM source_file", String.class));
        prepare.run(CsvSource.ALF01, false);
        assertEquals(JobStatus.SUCCESS, job.importFile(CsvSource.ALF01, false).status());
        assertEquals(20.0, sql.queryForObject("SELECT MIN(employment_rate_percent) FROM annual_employment_rate", Double.class));

        // Reverting to an older successful input still requires replacing the current snapshot.
        Files.writeString(file, original);
        prepare.run(CsvSource.ALF01, false);
        assertEquals(JobStatus.SUCCESS, job.importFile(CsvSource.ALF01, false).status());
        assertEquals(10.0, sql.queryForObject("SELECT MIN(employment_rate_percent) FROM annual_employment_rate", Double.class));

        Files.writeString(file, original.replace(",%,10", ",%,invalid"));
        assertThrows(IllegalStateException.class, () -> job.importFile(CsvSource.ALF01, false));
        assertEquals(checksum, sql.queryForObject("SELECT checksum FROM source_file", String.class));
        assertEquals(7L, sql.queryForObject("SELECT COUNT(*) FROM annual_employment_rate", Long.class));
        Files.writeString(file, original);

        // Force a database failure after DELETE to verify an actual InnoDB rollback.
        sql.execute("CREATE TRIGGER reject_annual BEFORE INSERT ON annual_employment_rate "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Simulated failure'");
        try {
            assertThrows(Exception.class, () -> job.importFile(CsvSource.ALF01, true));
        } finally {
            sql.execute("DROP TRIGGER reject_annual");
        }
        assertEquals(7L, sql.queryForObject("SELECT COUNT(*) FROM annual_employment_rate", Long.class));
        assertEquals("FAILED", sql.queryForObject("SELECT status FROM job_run ORDER BY id DESC LIMIT 1", String.class));
        assertEquals(0L, sql.queryForObject("SELECT COUNT(*) FROM job_run WHERE status = 'RUNNING'", Long.class));

        store.locked(CsvSource.ALF01, session -> {
            assertThrows(IllegalStateException.class, () -> job.importFile(CsvSource.ALF01, false));
            return null;
        });
    }
}
