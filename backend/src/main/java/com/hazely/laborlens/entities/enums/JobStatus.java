package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum JobStatus {
    RUNNING("Running"),
    SUCCESS("Success"),
    FAILED("Failed"),
    SKIPPED("Skipped");

    private final String label;

    JobStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
