package com.hazely.laborlens.jobs;

import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JobFilesTests {
    @TempDir
    Path root;

    @Test
    void publishesAndArchivesTheSameBytesWithoutOverwritingEvidence() throws Exception {
        JobFiles files = new JobFiles(root.toString());
        var initial = JobFiles.Input.of("old".getBytes(StandardCharsets.UTF_8));
        var revised = JobFiles.Input.of("new".getBytes(StandardCharsets.UTF_8));
        files.publish(CsvSource.ALF01, initial);
        Path artifact = files.archive(1, CsvSource.ALF01, files.read(CsvSource.ALF01), "test-v1");
        files.publish(CsvSource.ALF01, revised);
        assertEquals(revised.checksum(), files.read(CsvSource.ALF01).checksum());
        assertEquals("old", Files.readString(artifact.resolve("ALF01.csv")));
        assertThrows(Exception.class, () -> files.archive(1, CsvSource.ALF01, revised, "test-v1"));
    }

    @Test
    void rejectsSymlinkSourceDirectories() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path data = Files.createDirectory(root.resolve("data"));
        Files.createSymbolicLink(data.resolve("cso"), outside);
        assertThrows(Exception.class, () -> new JobFiles(data.toString()).path(CsvSource.ALF01));
    }
}
