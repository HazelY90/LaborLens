package com.hazely.laborlens.jobs;

import org.junit.jupiter.api.Test;
import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import com.hazely.laborlens.entities.enums.JobType;
import com.hazely.laborlens.entities.enums.PolicyType;
import com.hazely.laborlens.jobs.policyExtraction.PdfSource;
import com.hazely.laborlens.jobs.policyExtraction.PolicyDraft;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobStoreTests {
    @Test
    void rollsBackPolicyReplacementWhenEvidenceCannotBeSaved() throws Exception {
        Connection db = mock(Connection.class);
        PreparedStatement select = mock(PreparedStatement.class);
        PreparedStatement delete = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet source = mock(ResultSet.class);
        ResultSet keys = mock(ResultSet.class);
        PdfSource pdf = PdfSource.STRATEGY_2025;
        when(db.prepareStatement(startsWith("SELECT id, checksum"))).thenReturn(select);
        when(select.executeQuery()).thenReturn(source);
        when(source.next()).thenReturn(true);
        when(source.getLong("id")).thenReturn(9L);
        when(source.getString("table_name")).thenReturn(pdf.table());
        when(source.getString("download_path")).thenReturn(pdf.path());
        when(source.getString("download_url")).thenReturn(pdf.url());
        when(db.prepareStatement("DELETE FROM policy WHERE source_file_id = ?")).thenReturn(delete);
        when(db.prepareStatement(startsWith("INSERT INTO policy"), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(insert);
        when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(42L);
        JobStore store = new JobStore(mock(DataSource.class));
        var policy = new PolicyDraft(pdf.fileName(), 2025, 2028, "Expand training.",
                PolicyType.SKILLS_DEVELOPMENT, 1, "Expand domestic skills training.");
        assertThrows(java.io.IOException.class, () -> store.new Session(db)
                .replacePolicies(pdf, 9, List.of(policy), 1, ids -> {
            assertEquals(List.of(42L), ids);
            throw new java.io.IOException("Simulated artifact failure");
        }));
        verify(delete).setLong(1, 9);
        verify(db).rollback();
        verify(db, never()).commit();
    }

    @Test
    void rollsBackDeleteWhenBatchInsertFails() throws Exception {
        Connection db = mock(Connection.class);
        Statement delete = mock(Statement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        when(db.createStatement()).thenReturn(delete);
        when(db.prepareStatement(startsWith("INSERT INTO annual"))).thenReturn(insert);
        when(insert.executeBatch()).thenThrow(new SQLException("Simulated batch failure"));
        JobStore store = new JobStore(mock(DataSource.class));
        var session = store.new Session(db);
        assertThrows(SQLException.class, () -> session.replace(CsvSource.ALF01,
                List.of(List.of(2025, "ALL", "ALL", "ALL", "IRELAND", 10.0)), 1));
        var order = inOrder(db, delete, insert);
        order.verify(db).setAutoCommit(false);
        order.verify(delete).executeUpdate("DELETE FROM annual_employment_rate");
        order.verify(insert).executeBatch();
        order.verify(db).rollback();
        order.verify(db).setAutoCommit(true);
        verify(db, never()).commit();
    }

    @Test
    void commitsRowsAndSuccessTogether() throws Exception {
        Connection db = mock(Connection.class);
        Statement delete = mock(Statement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        PreparedStatement finish = mock(PreparedStatement.class);
        when(db.createStatement()).thenReturn(delete);
        when(db.prepareStatement(startsWith("INSERT INTO annual"))).thenReturn(insert);
        when(db.prepareStatement(startsWith("UPDATE job_run SET status"))).thenReturn(finish);
        when(finish.executeUpdate()).thenReturn(1);
        JobStore store = new JobStore(mock(DataSource.class));
        store.new Session(db).replace(CsvSource.ALF01,
                List.of(List.of(2025, "ALL", "ALL", "ALL", "IRELAND", 10.0)), 1);
        var order = inOrder(db, insert, finish);
        order.verify(db).setAutoCommit(false);
        order.verify(insert).executeBatch();
        order.verify(finish).setString(1, "SUCCESS");
        order.verify(finish).executeUpdate();
        order.verify(db).commit();
        order.verify(db).setAutoCommit(true);
        verify(db, never()).rollback();
    }

    @Test
    void skipsOnlyTheLatestSuccessfulVersion() throws Exception {
        Connection db = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(db.prepareStatement(contains("ORDER BY id DESC LIMIT 1"))).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn("latest");
        when(rows.getString(2)).thenReturn("v2");
        JobStore store = new JobStore(mock(DataSource.class));
        var session = store.new Session(db);
        assertFalse(session.isCurrent(1, JobType.CSV_IMPORT, "older", "v1"));
        assertFalse(session.isCurrent(1, JobType.CSV_IMPORT, "latest", "v1"));
        assertTrue(session.isCurrent(1, JobType.CSV_IMPORT, "latest", "v2"));
    }

    @Test
    void refusesConcurrentSourceWork() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection db = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(db);
        when(db.getAutoCommit()).thenReturn(true);
        when(db.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getInt(1)).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> new JobStore(source).locked(CsvSource.ALF01, session -> fail("Must not run")));
        verify(db).close();
    }
}
