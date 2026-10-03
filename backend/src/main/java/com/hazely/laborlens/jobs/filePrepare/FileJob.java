package com.hazely.laborlens.jobs.filePrepare;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.entities.enums.JobType;
import com.hazely.laborlens.jobs.*;
import com.hazely.laborlens.jobs.metricsExtraction.CsvParser;
import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import com.hazely.laborlens.jobs.policyExtraction.PdfSource;
import com.hazely.laborlens.jobs.policyExtraction.PolicyCheck;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Prepares all nine files before any statistical or policy replacement starts. */
@Component
public class FileJob {
    private static final String VERSION = "file-v3";
    private final JobStore store;
    private final JobFiles files;
    private final FileDownload download;
    private final CsvParser csv = new CsvParser();

    public FileJob(JobStore store, JobFiles files, FileDownload download) {
        this.store = store;
        this.files = files;
        this.download = download;
    }

    public List<JobOutcome> runAll(boolean isRefresh) throws Exception {
        List<JobOutcome> outcomes = new ArrayList<>();
        for (CsvSource source : CsvSource.values()) outcomes.add(run(source, isRefresh));
        for (PdfSource source : PdfSource.values()) outcomes.add(run(source, isRefresh));
        return List.copyOf(outcomes);
    }

    public JobOutcome run(SourceSpec source, boolean isRefresh) throws Exception {
        return store.locked(source, session -> {
            long run = session.start(source, JobType.FILE_PREPARATION, null, null, VERSION);
            try {
                session.source(source);
                JobFiles.Input input = null;
                if (!isRefresh && Files.exists(files.path(source))) {
                    try {
                        input = files.read(source);
                        validate(source, input);
                    } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException error) {
                        input = null;
                    }
                }
                FileDownload.Download fetched = null;
                if (input == null) {
                    fetched = download.fetch(source);
                    input = fetched.input();
                    validate(source, input);
                }
                session.input(run, input.checksum());
                Path artifact = files.archive(run, source, input, VERSION);
                JobFiles.properties(artifact.resolve("acquisition.properties"), fetched == null
                        ? Map.of("mode", "local") : Map.of("mode", "download",
                        "url", fetched.url(),
                        "last_modified", fetched.modified(), "retrieved_at",
                        fetched.retrieved()));
                // Publish only validated bytes; registration can be retried against this file.
                if (fetched != null) files.publish(source, input);
                session.register(source, input.checksum(), run);
                return new JobOutcome(run, source.fileName(), JobStatus.SUCCESS, null);
            } catch (Exception error) {
                session.fail(run, error);
                throw error;
            }
        });
    }

    private void validate(SourceSpec source, JobFiles.Input input) throws Exception {
        if (source instanceof CsvSource item) csv.parse(item, input.bytes());
        else if (source instanceof PdfSource) PolicyCheck.validatePdf(input.bytes());
        else throw new IllegalArgumentException("Unknown source type");
    }
}
