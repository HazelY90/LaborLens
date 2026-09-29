package com.hazely.laborlens.jobs.metricsExtraction;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.entities.enums.JobType;
import com.hazely.laborlens.jobs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Imports registered CSV files; file preparation belongs exclusively to the first stage. */
@Component
public class CsvJob {
    private static final String VERSION = "csv-v1";
    private static final Logger log = LoggerFactory.getLogger(CsvJob.class);
    private final JobStore store;
    private final JobFiles files;
    private final CsvParser parser = new CsvParser();

    public CsvJob(JobStore store, JobFiles files) {
        this.store = store;
        this.files = files;
    }

    public List<JobOutcome> runAll(boolean isForce) throws Exception {
        List<JobOutcome> outcomes = new ArrayList<>();
        for (CsvSource source : CsvSource.values()) outcomes.add(importFile(source, isForce));
        return List.copyOf(outcomes);
    }

    public JobOutcome importFile(CsvSource source, boolean isForce) throws Exception {
        return store.locked(source, session -> {
            JobStore.Source registered = session.source(source);
            if (registered == null) throw new IllegalStateException("Run file preparation before CSV import");
            JobFiles.Input input = files.read(source);
            long run = session.start(source, JobType.CSV_IMPORT, registered, input.checksum(), VERSION);
            try {
                Path artifact = files.archive(run, source, input, VERSION);
                if (!registered.checksum().equals(input.checksum())) {
                    throw new IllegalStateException("Local file changed; run file preparation again");
                }
                if (!isForce && session.isCurrent(registered.id(), JobType.CSV_IMPORT, input.checksum(), VERSION)) {
                    session.finish(run, JobStatus.SKIPPED, null, null);
                    return new JobOutcome(run, source.fileName(), JobStatus.SKIPPED, null);
                }
                CsvParser.Result parsed = parser.parse(source, input.bytes());
                JobFiles.properties(artifact.resolve("counts.properties"), Map.of(
                        "retained", Integer.toString(parsed.rows().size()),
                        "excluded", Long.toString(parsed.excluded()),
                        "missing", Long.toString(parsed.missing()), "rejected", "0"));
                session.replace(source, parsed.rows(), run);
                log.info("Job {} imported {} retained={} excluded={} missing={} rejected=0", run,
                        source.fileName(), parsed.rows().size(), parsed.excluded(), parsed.missing());
                return new JobOutcome(run, source.fileName(), JobStatus.SUCCESS, (long) parsed.rows().size());
            } catch (Exception error) {
                session.fail(run, error);
                throw error;
            }
        });
    }
}
