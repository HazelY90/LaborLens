package com.hazely.laborlens.entities.enums;

/** Stable policy categories with English display labels. */
public enum PolicyType {
    ECONOMIC_MIGRATION("Economic Migration"),
    EMPLOYMENT_DEVELOPMENT("Employment Development"),
    SKILLS_DEVELOPMENT("Skills Development"),
    WORKING_CONDITIONS("Working Conditions"),
    EMPLOYMENT_INCLUSION("Employment Inclusion");

    private final String label;

    PolicyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
