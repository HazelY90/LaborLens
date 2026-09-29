package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum JobType {
    FILE_PREPARATION("File Preparation"),
    CSV_IMPORT("CSV Import"),
    POLICY_EXTRACTION("Policy Extraction");

    private final String label;

    JobType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
