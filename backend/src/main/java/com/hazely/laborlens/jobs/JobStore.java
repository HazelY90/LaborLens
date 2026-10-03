package com.hazely.laborlens.jobs;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.entities.enums.JobType;
import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import com.hazely.laborlens.jobs.policyExtraction.PdfSource;
import com.hazely.laborlens.jobs.policyExtraction.PolicyDraft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Owns explicit transactions so failed replacements cannot erase run history. */
@Component
public class JobStore {
    private final DataSource dataSource;
    private final Clock clock;

    @Autowired
    public JobStore(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    JobStore(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Session session) throws Exception;
    }

    public <T> T locked(SourceSpec source, Work<T> work) throws Exception {
        return locked("laborlens:source:" + source.fileName(), work);
    }

    public <T> T pipeline(Work<T> work) throws Exception {
        return locked("laborlens:ingestion", work);
    }

    private <T> T locked(String lock, Work<T> work) throws Exception {
        // A connection-scoped MySQL lock coordinates local jobs across JVMs as well.
        try (Connection db = dataSource.getConnection()) {
            if (!db.getAutoCommit()) throw new IllegalStateException("Jobs require an autocommit connection");
            try (PreparedStatement stmt = db.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                stmt.setString(1, lock);
                try (ResultSet rows = stmt.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1) {
                        throw new IllegalStateException("Source job is already active");
                    }
                }
            }
            try {
                return work.run(new Session(db));
            } finally {
                try (PreparedStatement stmt = db.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    stmt.setString(1, lock);
                    stmt.execute();
                }
            }
        }
    }

    public record Source(long id, String checksum) {
    }

    public final class Session {
        private final Connection db;

        Session(Connection db) {
            this.db = db;
        }

        public Source source(SourceSpec source) throws SQLException {
            try (PreparedStatement stmt = db.prepareStatement(
                    "SELECT id, checksum, table_name, download_path, download_url FROM source_file WHERE file_name = ?")) {
                stmt.setString(1, source.fileName());
                try (ResultSet rows = stmt.executeQuery()) {
                    if (!rows.next()) return null;
                    if (!source.table().equals(rows.getString("table_name"))
                            || !source.path().equals(rows.getString("download_path"))
                            || !source.url().equals(rows.getString("download_url"))) {
                        throw new IllegalStateException("Source registration does not match approved routing");
                    }
                    return new Source(rows.getLong("id"), rows.getString("checksum"));
                }
            }
        }

        public long start(SourceSpec source, JobType type, Source registered, String checksum,
                String version)
                throws SQLException {
            try (PreparedStatement stmt = db.prepareStatement(
                    "INSERT INTO job_run (job_type, source_file_id, file_name, checksum, status, started_at, process_version) "
                    + "VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, type.name());
                stmt.setObject(2, registered == null ? null : registered.id());
                stmt.setString(3, source.fileName());
                stmt.setString(4, checksum);
                stmt.setObject(5, now());
                stmt.setString(6, version);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Job ID was not returned");
                    return keys.getLong(1);
                }
            }
        }

        public void register(SourceSpec source, String checksum, long run) throws Exception {
            transaction(session -> {
                try (PreparedStatement stmt = db.prepareStatement(
                        "INSERT INTO source_file (file_name, table_name, download_url, download_path, checksum) "
                        + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE checksum = ?")) {
                    stmt.setString(1, source.fileName());
                    stmt.setString(2, source.table());
                    stmt.setString(3, source.url());
                    stmt.setString(4, source.path());
                    stmt.setString(5, checksum);
                    stmt.setString(6, checksum);
                    stmt.executeUpdate();
                }
                Source registered = source(source);
                try (PreparedStatement stmt = db.prepareStatement(
                        "UPDATE job_run SET source_file_id = ?, checksum = ? WHERE id = ?")) {
                    stmt.setLong(1, registered.id());
                    stmt.setString(2, checksum);
                    stmt.setLong(3, run);
                    stmt.executeUpdate();
                }
                finish(run, JobStatus.SUCCESS, null, null);
                return null;
            });
        }

        public void input(long run, String checksum) throws SQLException {
            try (PreparedStatement stmt = db.prepareStatement("UPDATE job_run SET checksum = ? WHERE id = ?")) {
                stmt.setString(1, checksum);
                stmt.setLong(2, run);
                stmt.executeUpdate();
            }
        }

        public boolean isCurrent(long source, JobType type, String checksum, String version) throws SQLException {
            // Historical matches are insufficient after a different version was committed.
            try (PreparedStatement stmt = db.prepareStatement(
                    "SELECT checksum, process_version FROM job_run WHERE source_file_id = ? "
                    + "AND job_type = ? AND status = 'SUCCESS' ORDER BY id DESC LIMIT 1")) {
                stmt.setLong(1, source);
                stmt.setString(2, type.name());
                try (ResultSet rows = stmt.executeQuery()) {
                    return rows.next() && checksum.equals(rows.getString(1)) && version.equals(rows.getString(2));
                }
            }
        }

        public void replace(CsvSource source, List<List<Object>> rows, long run) throws Exception {
            transaction(session -> {
                try (Statement stmt = db.createStatement()) {
                    stmt.executeUpdate("DELETE FROM " + source.table());
                }
                try (PreparedStatement stmt = db.prepareStatement(source.insertSql())) {
                    int count = 0;
                    for (List<Object> row : rows) {
                        for (int i = 0; i < row.size(); i++) stmt.setObject(i + 1,
                                row.get(i));
                        stmt.addBatch();
                        if (++count % 500 == 0) {
                            stmt.executeBatch();
                            stmt.clearBatch();
                        }
                    }
                    if (count % 500 != 0) stmt.executeBatch();
                }
                finish(run, JobStatus.SUCCESS, (long) rows.size(), null);
                return null;
            });
        }

        /** The evidence callback runs before commit so its failure also rolls back policies. */
        public void replacePolicies(PdfSource source, long sourceId, List<PolicyDraft> policies,
                long run, PolicyEvidence evidence) throws Exception {
            Source registered = source(source);
            if (registered == null || registered.id() != sourceId) {
                throw new IllegalStateException("Policy source is not registered");
            }
            transaction(session -> {
                try (PreparedStatement stmt = db.prepareStatement("DELETE FROM policy WHERE source_file_id = ?")) {
                    stmt.setLong(1, sourceId);
                    stmt.executeUpdate();
                }
                var ids = new java.util.ArrayList<Long>();
                try (PreparedStatement stmt = db.prepareStatement(
                        "INSERT INTO policy (period_start, period_end, policy, type, source_file_id) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    for (PolicyDraft policy : policies) {
                        stmt.setInt(1, source.start());
                        stmt.setInt(2, source.end());
                        stmt.setString(3, policy.policy());
                        stmt.setString(4, policy.type().name());
                        stmt.setLong(5, sourceId);
                        stmt.executeUpdate();
                        try (ResultSet keys = stmt.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Policy ID was not returned");
                            ids.add(keys.getLong(1));
                        }
                    }
                }
                evidence.write(List.copyOf(ids));
                finish(run, JobStatus.SUCCESS, (long) policies.size(), null);
                return null;
            });
        }

        public void fail(long run, Exception error) {
            // Never persist provider responses, source cells, connection strings or credentials.
            String reason = error instanceof IllegalArgumentException ? "Input or output validation failed"
                    : error instanceof IllegalStateException ? "Job state validation failed"
                    : error instanceof java.io.IOException ? "File access or download failed"
                    : "Database or processing operation failed";
            try {
                finish(run, JobStatus.FAILED, null, reason);
            } catch (SQLException failure) {
                error.addSuppressed(failure);
            }
        }

        public void finish(long run, JobStatus status, Long count, String error) throws SQLException {
            try (PreparedStatement stmt = db.prepareStatement(
                    "UPDATE job_run SET status = ?, finished_at = GREATEST(started_at, ?), "
                    + "row_count = ?, error_message = ? WHERE id = ? AND status = 'RUNNING'")) {
                stmt.setString(1, status.name());
                stmt.setObject(2, now());
                stmt.setObject(3, count);
                stmt.setString(4, error);
                stmt.setLong(5, run);
                if (stmt.executeUpdate() != 1) throw new SQLException("Job is not running");
            }
        }

        private <T> T transaction(Work<T> work) throws Exception {
            db.setAutoCommit(false);
            try {
                T result = work.run(this);
                db.commit();
                return result;
            } catch (Exception error) {
                try {
                    db.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            } finally {
                db.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    public interface PolicyEvidence {
        void write(List<Long> ids) throws Exception;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
