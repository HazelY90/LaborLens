package com.hazely.laborlens.jobs;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.jobs.metricsExtraction.CsvParserTests;
import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import com.hazely.laborlens.jobs.filePrepare.FileDownload;
import com.hazely.laborlens.jobs.filePrepare.FileJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileJobTests {
    @TempDir
    Path root;
    private final Connection db = mock(Connection.class);
    private final PreparedStatement update = mock(PreparedStatement.class);
    private final FileDownload download = mock(FileDownload.class);

    @Test
    void failedRefreshPreservesTheExistingFileAndRegistry() throws Exception {
        JobFiles files = new JobFiles(root.toString());
        Path file = files.path(CsvSource.ALF01);
        Files.writeString(file, CsvParserTests.sample());
        when(download.fetch(CsvSource.ALF01)).thenReturn(fetched("not a CSV"));
        FileJob job = job(files);
        assertThrows(IllegalArgumentException.class, () -> job.run(CsvSource.ALF01, true));
        assertEquals(CsvParserTests.sample(), Files.readString(file));
        verify(db, never()).prepareStatement(startsWith("INSERT INTO source_file"));
        verify(update).setString(1, "FAILED");
        verify(db, never()).commit();
    }

    @Test
    void downloadsAndRegistersMissingFiles() throws Exception {
        JobFiles files = new JobFiles(root.toString());
        when(download.fetch(CsvSource.ALF01)).thenReturn(fetched(CsvParserTests.sample()));
        assertEquals(JobStatus.SUCCESS, job(files).run(CsvSource.ALF01, false).status());
        assertEquals(CsvParserTests.sample(), Files.readString(files.path(CsvSource.ALF01)));
        verify(db).commit();
        verify(update).setString(1, "SUCCESS");
    }

    @Test
    void reusesValidLocalFilesWithoutDownloading() throws Exception {
        JobFiles files = new JobFiles(root.toString());
        Files.writeString(files.path(CsvSource.ALF01), CsvParserTests.sample());
        assertEquals(JobStatus.SUCCESS, job(files).run(CsvSource.ALF01, false).status());
        verifyNoInteractions(download);
        verify(db).commit();
    }

    private FileJob job(JobFiles files) throws Exception {
        var store = mock(JobStore.class);
        var session = new JobStore(mock(DataSource.class)).new Session(db);
        when(store.locked(eq(CsvSource.ALF01), any())).thenAnswer(call -> {
            JobStore.Work<?> work = call.getArgument(1);
            return work.run(session);
        });
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(db.prepareStatement(anyString())).thenReturn(update);
        when(db.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(insert);
        when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(1L);
        when(update.executeUpdate()).thenReturn(1);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet source = mock(ResultSet.class);
        when(db.prepareStatement(startsWith("SELECT id, checksum"))).thenReturn(select);
        when(select.executeQuery()).thenReturn(source);
        when(source.next()).thenReturn(false, true);
        when(source.getLong("id")).thenReturn(1L);
        when(source.getString("table_name")).thenReturn(CsvSource.ALF01.table());
        when(source.getString("download_path")).thenReturn(CsvSource.ALF01.path());
        when(source.getString("download_url")).thenReturn(CsvSource.ALF01.url());
        when(source.getString("checksum")).thenReturn(fetched(CsvParserTests.sample()).input().checksum());
        return new FileJob(store, files, download);
    }

    private static FileDownload.Download fetched(String text) {
        return new FileDownload.Download(JobFiles.Input.of(text.getBytes(StandardCharsets.UTF_8)),
                CsvSource.ALF01.url(), "", "2026-09-29T00:00:00Z");
    }
}
