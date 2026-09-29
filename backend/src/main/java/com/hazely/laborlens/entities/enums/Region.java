package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum Region {
    IRELAND("Ireland"),
    NORTHERN_WESTERN("Northern and Western"),
    SOUTHERN("Southern"),
    EASTERN_MIDLAND("Eastern and Midland");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
