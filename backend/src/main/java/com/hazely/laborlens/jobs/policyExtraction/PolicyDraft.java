package com.hazely.laborlens.jobs.policyExtraction;

import com.hazely.laborlens.entities.enums.PolicyType;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Evidence uses one-based physical PDF page numbers, not printed page labels. */
public record PolicyDraft(@JsonProperty("source_file") String sourceFile,
                          @JsonProperty("period_start") int periodStart,
                          @JsonProperty("period_end") int periodEnd, String policy,
                          PolicyType type, int page, String quote) {}
