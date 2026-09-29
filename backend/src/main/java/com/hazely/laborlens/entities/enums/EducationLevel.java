package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum EducationLevel {
    ALL("Levels of Education (Levels 0-8)"),
    PRIMARY_LOWER_SECONDARY("Primary and lower secondary education (Levels 1-2)"),
    UPPER_POST_SECONDARY("Upper secondary and post-secondary non-tertiary education (Levels 3 and 4)"),
    TERTIARY("Tertiary education (Levels 5-8)");

    private final String label;

    EducationLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
