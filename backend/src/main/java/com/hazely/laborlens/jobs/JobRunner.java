package com.hazely.laborlens.jobs;

import com.hazely.laborlens.jobs.metricsExtraction.CsvJob;
import com.hazely.laborlens.jobs.filePrepare.FileJob;
import com.hazely.laborlens.jobs.policyExtraction.PdfJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One manual entry point, with strict barriers between the three stages. */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class JobRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private final JobStore store;
    private final FileJob files;
    private final CsvJob csv;
    private final PdfJob pdf;
    private final boolean isRefresh;
    private final boolean isForce;

    public JobRunner(JobStore store, FileJob files, CsvJob csv, PdfJob pdf,
                     @Value("${app.jobs.refresh:false}") boolean isRefresh,
                     @Value("${app.jobs.force:false}") boolean isForce) {
        this.store = store;
        this.files = files;
        this.csv = csv;
        this.pdf = pdf;
        this.isRefresh = isRefresh;
        this.isForce = isForce;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        store.pipeline(session -> {
            log.info("Ingestion stage 1/3: prepare and register all source files");
            files.runAll(isRefresh);
            log.info("Ingestion stage 2/3: import all four CSV files");
            csv.runAll(isForce);
            log.info("Ingestion stage 3/3: extract policies from all five PDFs");
            pdf.runAll(isForce);
            log.info("All ingestion stages completed");
            return null;
        });
    }
}
