package com.hazely.laborlens.jobs.policyExtraction;

import com.hazely.laborlens.entities.enums.JobStatus;
import com.hazely.laborlens.entities.enums.JobType;
import com.hazely.laborlens.jobs.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Replaces only one report's policies after the full model response passes structural validation. */
@Component
public class PdfJob {
    private final JobStore store;
    private final JobFiles files;
    private final PolicyAi ai;
    private final JsonMapper json = JsonMapper.builder().build();

    public PdfJob(JobStore store, JobFiles files, PolicyAi ai) {
        this.store = store;
        this.files = files;
        this.ai = ai;
    }

    public List<JobOutcome> runAll(boolean isForce) throws Exception {
        List<JobOutcome> outcomes = new ArrayList<>();
        for (PdfSource source : PdfSource.values()) outcomes.add(run(source, isForce));
        return List.copyOf(outcomes);
    }

    public JobOutcome run(PdfSource source, boolean isForce) throws Exception {
        String version = ai.version();
        return store.locked(source, session -> {
            JobStore.Source registered = session.source(source);
            if (registered == null) throw new IllegalStateException("Run file preparation before PDF processing");
            JobFiles.Input input = files.read(source);
            long run = session.start(source, JobType.POLICY_EXTRACTION, registered, input.checksum(), version);
            try {
                var artifact = files.archive(run, source, input, version);
                if (!registered.checksum().equals(input.checksum())) {
                    throw new IllegalStateException("Local PDF changed; run file preparation again");
                }
                if (!isForce && session.isCurrent(registered.id(), JobType.POLICY_EXTRACTION, input.checksum(), version)) {
                    session.finish(run, JobStatus.SKIPPED, null, null);
                    return new JobOutcome(run, source.fileName(), JobStatus.SKIPPED, null);
                }
                var policies = ai.extract(source, input.bytes(), artifact);
                session.replacePolicies(source, registered.id(), policies, run, ids -> {
                    List<Map<String, Object>> evidence = new ArrayList<>();
                    for (int i = 0; i < ids.size(); i++) {
                        evidence.add(Map.of("policy_id", ids.get(i), "policy", policies.get(i)));
                    }
                    Files.writeString(artifact.resolve("evidence.json"), json.writeValueAsString(evidence), StandardOpenOption.CREATE_NEW);
                });
                return new JobOutcome(run, source.fileName(), JobStatus.SUCCESS, (long) policies.size());
            } catch (Exception error) {
                session.fail(run, error);
                throw error;
            }
        });
    }
}
