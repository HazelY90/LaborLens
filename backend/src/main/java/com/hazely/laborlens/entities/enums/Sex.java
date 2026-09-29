package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum Sex {
    ALL("Both sexes"),
    FEMALE("Female"),
    MALE("Male");

    private final String label;

    Sex(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
