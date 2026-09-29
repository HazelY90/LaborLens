package com.hazely.laborlens.jobs;

import com.hazely.laborlens.jobs.metricsExtraction.CsvJob;
import com.hazely.laborlens.jobs.filePrepare.FileJob;
import com.hazely.laborlens.jobs.policyExtraction.PdfJob;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobRunnerTests {
    private final JobStore store = mock(JobStore.class);
    private final FileJob files = mock(FileJob.class);
    private final CsvJob csv = mock(CsvJob.class);
    private final PdfJob pdf = mock(PdfJob.class);

    @Test
    void runsAllPreparationBeforeCsvAndPdf() throws Exception {
        unlock();
        new JobRunner(store, files, csv, pdf, true, false).run(null);
        var order = inOrder(files, csv, pdf);
        order.verify(files).runAll(true);
        order.verify(csv).runAll(false);
        order.verify(pdf).runAll(false);
        verifyNoMoreInteractions(files, csv, pdf);
    }

    @Test
    void stopsBeforeProcessingWhenPreparationFails() throws Exception {
        unlock();
        when(files.runAll(false)).thenThrow(new IllegalStateException("Failed preparation"));
        assertThrows(IllegalStateException.class,
                () -> new JobRunner(store, files, csv, pdf, false, false).run(null));
        verifyNoInteractions(csv, pdf);
    }

    @Test
    void stopsBeforeAiWhenCsvFails() throws Exception {
        unlock();
        when(csv.runAll(false)).thenThrow(new IllegalStateException("Failed CSV import"));
        assertThrows(IllegalStateException.class,
                () -> new JobRunner(store, files, csv, pdf, false, false).run(null));
        verifyNoInteractions(pdf);
    }

    @Test
    void requiresAnExplicitStartupFlag() {
        var context = new ApplicationContextRunner().withUserConfiguration(JobRunner.class)
                .withBean(JobStore.class, () -> store).withBean(FileJob.class, () -> files)
                .withBean(CsvJob.class, () -> csv).withBean(PdfJob.class, () -> pdf);
        context.run(app -> assertFalse(app.containsBean("jobRunner")));
        context.withPropertyValues("app.jobs.enabled=true").run(app -> assertEquals(1,
                app.getBeansOfType(JobRunner.class).size()));
        verifyNoInteractions(files, csv, pdf);
    }

    private void unlock() throws Exception {
        when(store.pipeline(any())).thenAnswer(call -> {
            JobStore.Work<?> work = call.getArgument(0);
            return work.run(null);
        });
    }
}
