package com.hazely.laborlens.jobs;

import com.hazely.laborlens.entities.enums.JobStatus;

/** Each stage returns one outcome per input file, matching the job_run schema. */
public record JobOutcome(long runId, String fileName, JobStatus status, Long rowCount) {}
